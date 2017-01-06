(ns driver.generator
  (:require [clojure.tools.logging :refer [info warn]]
            [clojure.core.async :refer [thread offer! >!!]]
            [driver.channels :refer [channels]]
            [driver.store :as store]
            [environ.core :refer [env]]
            [mount.core :refer [defstate]]))

(def ^:private second-in-milliseconds 1000)
(def ^:private min-sleep-time 20) ; in ms, do not bother sleeping if adjusted sleep time is less than this
(def ^:private average-oversleep 2) ; in ms, observed to be typical oversleep amount of Thread/sleep method
(def ^:private normal-batch-size 10) ; target generating this many cases at a time
(def ^:private jumbo-batch-size 1000) ; target generating this many cases at a time

(def ^:private smooth-operator? (= "true" (or (:smooth-operator env)
                                    "true")))

(def config {:smooth-operator smooth-operator?})

(defn- >!!-verbose [ch-name ch payload]
  (when-not (offer! ch payload)
    (info ch-name "channel is full")
    (>!! ch payload)))

(defn- sleep-time-and-cases-to-gen [org-id target-rate]
  (cond
    (or (< target-rate normal-batch-size)
        (not smooth-operator?))
    [second-in-milliseconds target-rate]

    (< target-rate jumbo-batch-size)
    [(float (* (/ second-in-milliseconds target-rate)
               normal-batch-size))
     normal-batch-size]

    :else
    [(float (* (/ second-in-milliseconds target-rate)
               jumbo-batch-size))
     jumbo-batch-size]))

(defn- start [quit-atom]
  (doseq [org-id (store/org-ids)]
    (let [case-data-template {:org org-id :text "foo" :prediction_type "sentiment"}
          gen-data (partial assoc case-data-template :case)]
      (thread
        (loop [i 0 ideal-start-time nil previous-target-rate 0]
          (when-not @quit-atom
            (let [actual-start-time (System/currentTimeMillis)
                  target-rate (store/target-rate org-id)
                  start-time (if (and ideal-start-time
                                      (= target-rate previous-target-rate))
                               ideal-start-time
                               actual-start-time)
                  generated-cases-chan (:generated-cases channels)
                  stats-chan (:stats channels)
                  [sleep-time batch-size] (sleep-time-and-cases-to-gen org-id target-rate)
                  gen-res (->> batch-size
                               (+ i)
                               (range i)
                               (map #(offer! generated-cases-chan (gen-data %))))
                  skipped? (atom false)
                  _ (doseq [res gen-res]
                      (when-not res
                        (reset! skipped? true)
                        (>!!-verbose "Stats" stats-chan [:finish {:org org-id} :skip])))
                  adjusted-sleep-time (max 0
                                           (- sleep-time
                                              (- (System/currentTimeMillis) start-time)
                                              average-oversleep))
                  sleep-msg (str "sleeping for " adjusted-sleep-time "ms")
                  _ (when @skipped?
                      (warn "Skipped some of" batch-size "sample cases for org" org-id "and" sleep-msg))
                  _ (when (> adjusted-sleep-time min-sleep-time)
                      (Thread/sleep adjusted-sleep-time))]
              (recur (+ i batch-size) (+ start-time sleep-time) target-rate))))))))

(defstate ^:private generator
  :start
  (let [quit-atom (atom false)]
    (start quit-atom)
    quit-atom)
  :stop
  (reset! generator true))

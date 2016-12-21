(ns driver.generator
  (:require [clojure.tools.logging :refer [info warn]]
            [clojure.core.async :refer [thread offer! put!]]
            [driver.channels :refer [channels]]
            [driver.store :as store]
            [environ.core :refer [env]]
            [mount.core :refer [defstate]]))

(def ^:private second-in-milliseconds 1000)
(def ^:private min-sleep-time 20) ; in ms, do not bother sleeping if adjusted sleep time is less than this
(def ^:private average-oversleep 2) ; in ms, observed to be typical oversleep amount of Thread/sleep method
(def ^:private normal-batch-size 10) ; target generating this many cases at a time

(def ^:private smooth-operator? (= "true" (or (:smooth-operator env)
                                    "true")))

(def config {:smooth-operator smooth-operator?})

(defn- sleep-time-and-cases-to-gen [org-id target-rate]
  (if (or (< target-rate normal-batch-size)
          (not smooth-operator?))
    [second-in-milliseconds target-rate]
    [(float (* (/ second-in-milliseconds target-rate)
               normal-batch-size))
     normal-batch-size]))

(defn- start [quit-atom]
  (doseq [org-id (store/org-ids)]
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
                gen-data (fn [n] {:org org-id :text "foo" :case n :prediction_type "sentiment"})
                [sleep-time batch-size] (sleep-time-and-cases-to-gen org-id target-rate)
                gen-res (->> batch-size
                             (+ i)
                             (range i)
                             (map #(offer! generated-cases-chan (gen-data %))))
                _ (doseq [res gen-res]
                    (when-not res
                      (put! stats-chan [:finish {:org org-id} :skip])))
                all-sent? (every? true? gen-res)
                adjusted-sleep-time (max 0
                                         (- sleep-time
                                            (- (System/currentTimeMillis) start-time)
                                            average-oversleep))
                sleep-msg (str "sleeping for " adjusted-sleep-time "ms")
                _ (if all-sent?
                    (comment (info "Generated" batch-size "cases and" sleep-msg))
                    (warn "Dropped some of" batch-size "sample cases for org" org-id "and" sleep-msg))
                _ (when (> adjusted-sleep-time min-sleep-time)
                    (Thread/sleep adjusted-sleep-time))]
            (recur (+ i batch-size) (+ start-time sleep-time) target-rate)))))))

(defstate ^:private generator
  :start
  (let [quit-atom (atom false)]
    (start quit-atom)
    quit-atom)
  :stop
  (reset! generator true))

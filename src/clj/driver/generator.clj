(ns driver.generator
  (:require [clojure.tools.logging :refer [info warn]]
            [clojure.core.async :refer [thread offer! >!!]]
            [driver.channels :refer [channels]]
            [driver.store :as store]
            [mount.core :refer [defstate]]))

(def ^:private second-in-nanoseconds (* 1 1000 1000 1000))
(def ^:private fudge-factor 4300) ; 0.0043ms in ns

(defn- sleep-time-and-cases-to-gen [org-id]
  (let [target-rate(store/target-rate org-id)]
    (cond
      (zero? target-rate) [(/ second-in-nanoseconds 10) 0]
      (<= target-rate 1000) [(/ second-in-nanoseconds target-rate) 1] ;; in nanoseconds
      :else [second-in-nanoseconds target-rate])))

(defn- ns->ms [t]
  (float (/ t 1000 1000)))

(defn- start [quit-atom]
  (doseq [org-id (store/org-ids)]
    (thread
      (loop [i 0 sleep-adjustment 0]
        (when-not @quit-atom
          (let [start-time (System/nanoTime)
                generated-cases-chan (:generated-cases channels)
                stats-chan (:stats channels)
                gen-data (fn [n] {:org org-id :text "foo" :case n :prediction_type "sentiment"})
                [sleep-time batch-size] (sleep-time-and-cases-to-gen org-id)
                adjusted-sleep-time (fn []
                                      (max 0
                                           (- sleep-time
                                              (- (System/nanoTime) start-time)
                                              sleep-adjustment)))
                gen-res (->> batch-size
                             (+ i)
                             (range i)
                             (map #(offer! generated-cases-chan (gen-data %))))
                _ (doseq [res gen-res]
                    (when-not res
                      (>!! stats-chan [:finish {:org org-id} :skip])))
                all-sent? (every? true? gen-res)
                generation-time (- (System/nanoTime) start-time)
                sleep-msg (str "sleeping for " (ns->ms (adjusted-sleep-time)) "ms")
                _ (when-not all-sent?
                    (warn "Dropped some of" batch-size "sample cases for org" org-id "and" sleep-msg))
                sleep-time-precise (adjusted-sleep-time)
                sleep-start-time (System/nanoTime)
                _ (java.util.concurrent.locks.LockSupport/parkNanos sleep-time-precise)
                actual-sleep-time (- (System/nanoTime) sleep-start-time)
                sleep-error (- actual-sleep-time sleep-time-precise)
                ;; accounting for some short time spent outside of measured frames
                sleep-error (+ sleep-error fudge-factor)]
            (recur (+ i batch-size) sleep-error)))))))

(defstate ^:private generator
  :start
  (let [quit-atom (atom false)]
    (start quit-atom)
    quit-atom)
  :stop
  (reset! generator true))

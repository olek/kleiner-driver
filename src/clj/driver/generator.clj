(ns driver.generator
  (:require [clojure.tools.logging :refer [debug]]
            [clojure.core.async :refer [thread >!!]]
            [driver.channels :refer [channels]]
            [driver.store :as store]
            [mount.core :refer [defstate]]))

(defn sleep-time-and-cases-to-gen [org-id]
  (let [target-rate(store/target-rate org-id)]
    (cond
      (zero? target-rate) [100 0]
      (<= target-rate 100) [(/ 1000 target-rate) 1]
      :else [1000 target-rate])))

(defn- start [quit-atom]
  (doseq [org-id (store/org-ids)]
    (thread
      (let [generated-cases-chan (:generated-cases channels)]
        (loop [i 0]
          (when-not @quit-atom
            (let [data (fn [n] {:org org-id :text "foo" :case n :prediction_type "sentiment"})
                  [sleep-time cases-to-gen] (sleep-time-and-cases-to-gen org-id)
                  start-time (System/currentTimeMillis)
                  all-sent? (->> cases-to-gen
                                 (+ i)
                                 (range i)
                                 (map #(>!! generated-cases-chan (data %)))
                                 (every? true?))
                  generation-time (- (System/currentTimeMillis) start-time)
                  sleep-time (- sleep-time generation-time)
                  sleep-time (if (neg? sleep-time)
                               0 ; generation of cases took more than time allotted
                               sleep-time)
                  ]
              (when (not (zero? cases-to-gen))
                (debug "Generated" cases-to-gen "sample case(s) for org " org-id ", will sleep for" sleep-time "ms"))
              (when all-sent?
                (Thread/sleep sleep-time)
                (recur (+ i cases-to-gen))))))))))

(defstate ^:private generator
  :start
  (let [quit-atom (atom false)]
    (start quit-atom)
    quit-atom)
  :stop
  (reset! generator true))

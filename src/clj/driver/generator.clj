(ns driver.generator
  (:require [clojure.tools.logging :refer [debug warn]]
            [clojure.core.async :refer [thread offer!]]
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
        (loop [i 0]
          (when-not @quit-atom
            (let [generated-cases-chan (:generated-cases channels)
                  gen-data (fn [n] {:org org-id :text "foo" :case n :prediction_type "sentiment"})
                  [sleep-time cases-to-gen] (sleep-time-and-cases-to-gen org-id)
                  start-time (System/currentTimeMillis)
                  all-sent? (->> cases-to-gen
                                 (+ i)
                                 (range i)
                                 (map #(offer! generated-cases-chan (gen-data %)))
                                 (every? true?))
                  generation-time (- (System/currentTimeMillis) start-time)
                  sleep-time (- sleep-time generation-time)
                  sleep-time (if (neg? sleep-time)
                               0 ; generation of cases took more than time allotted
                               sleep-time)]
              (when-not all-sent?
                (warn "Dropped some sample cases for org" org-id))
              (when (not (zero? cases-to-gen))
                (debug "Generated" cases-to-gen "sample case(s) for org" org-id ", will sleep for" sleep-time "ms"))
                (Thread/sleep sleep-time)
                (recur (+ i cases-to-gen))))))))

(defstate ^:private generator
  :start
  (let [quit-atom (atom false)]
    (start quit-atom)
    quit-atom)
  :stop
  (reset! generator true))

(ns driver.generator
  (:require [clojure.tools.logging :refer [info warn]]
            [clojure.core.async :refer [thread offer!]]
            [driver.channels :refer [channels]]
            [driver.store :as store]
            [mount.core :refer [defstate]]))

(defn- start [quit-atom]
  (doseq [org-id (store/org-ids)]
    (thread
      (loop [i 0]
        (when-not @quit-atom
          (let [generated-cases-chan (:generated-cases channels)
                gen-data (fn [n] {:org org-id :text "foo" :case n :prediction_type "sentiment"})
                batch-size (store/target-rate org-id)
                start-time (System/currentTimeMillis)
                all-sent? (->> batch-size
                               (+ i)
                               (range i)
                               (map #(offer! generated-cases-chan (gen-data %)))
                               (every? true?))
                generation-time (- (System/currentTimeMillis) start-time)
                sleep-time (max 0
                                (- 1000 generation-time))]
            (if all-sent?
              (when (not (zero? batch-size))
                (info "Generated" batch-size "sample case(s) for org" org-id "and sleeping for" sleep-time "ms"))
              (warn "Dropped some of" batch-size "sample cases for org" org-id))

            (Thread/sleep sleep-time)
            (recur (+ i batch-size))))))))

(defstate ^:private generator
  :start
  (let [quit-atom (atom false)]
    (start quit-atom)
    quit-atom)
  :stop
  (reset! generator true))

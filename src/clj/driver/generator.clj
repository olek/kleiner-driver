(ns driver.generator
  (:require [clojure.tools.logging :refer [info warn]]
            [clojure.core.async :refer [thread offer! >!!]]
            [driver.channels :refer [channels]]
            [driver.store :as store]
            [mount.core :refer [defstate]]))

(def ^:private second-in-nanoseconds (* 1 1000 1000 1000))

(defn- sleep-time-and-cases-to-gen [org-id]
  (let [target-rate(store/target-rate org-id)]
    (cond
      (zero? target-rate) [second-in-nanoseconds 0]
      ;;:else [(/ second-in-nanoseconds target-rate) 1])))
      :else [second-in-nanoseconds target-rate])))

(defn- ns->ms [t]
  (float (/ t 1000 1000)))

(defn- start [quit-atom]
  (doseq [org-id (store/org-ids)]
    (thread
      (loop [i 0 ideal-start-time nil]
        (when-not @quit-atom
          (let [start-time (or ideal-start-time
                               (System/nanoTime))
                generated-cases-chan (:generated-cases channels)
                stats-chan (:stats channels)
                gen-data (fn [n] {:org org-id :text "foo" :case n :prediction_type "sentiment"})
                [sleep-time batch-size] (sleep-time-and-cases-to-gen org-id)
                adjusted-sleep-time (fn []
                                      (max 0
                                           (- sleep-time
                                              (- (System/nanoTime) start-time))))
                gen-res (->> batch-size
                             (+ i)
                             (range i)
                             (map #(offer! generated-cases-chan (gen-data %))))
                _ (doseq [res gen-res]
                    (when-not res
                      (>!! stats-chan [:finish {:org org-id} :skip])))
                all-sent? (every? true? gen-res)
                sleep-msg (str "sleeping for " (ns->ms (adjusted-sleep-time)) "ms")
                ;;_ (info "Generated" batch-size "cases and" sleep-msg)
                _ (when-not all-sent?
                    (warn "Dropped some of" batch-size "sample cases for org" org-id "and" sleep-msg))
                sleep-time-precise (adjusted-sleep-time)
                _ (Thread/sleep (ns->ms sleep-time-precise))]
            (recur (+ i batch-size) (+ start-time sleep-time))))))))

(defstate ^:private generator
  :start
  (let [quit-atom (atom false)]
    (start quit-atom)
    quit-atom)
  :stop
  (reset! generator true))

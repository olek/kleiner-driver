(ns driver.generator
  (:require [clojure.tools.logging :refer [info]]
            [clojure.core.async :refer [thread >!!]]
            [driver.channels :refer [channels]]
            [driver.store :as store]
            [mount.core :refer [defstate]]))

(defn sleep-time-and-cases-to-gen [org-id]
  (let [target-rate(store/target-rate org-id)]
    (if (zero? target-rate)
      [100 0]
      [(/ 1000 target-rate) 1])))

(defstate ^:private generator
  :start
  (let [quit-atom (atom false)]
    (thread
      (let [generated-cases-chan (:generated-cases channels)]
        (loop [i 0]
          (when-not @quit-atom
            (let [data (fn [n] {:org-id 123 :description "foo" :id n})
                  [sleep-time cases-to-gen] (sleep-time-and-cases-to-gen 123)
                  all-sent? (->> cases-to-gen
                                 (+ i)
                                 (range i)
                                 (map #(>!! generated-cases-chan (data %)))
                                 (every? true?))]
              (when (not (zero? cases-to-gen))
                (info "Generated" cases-to-gen "sample case, will sleep for" sleep-time "ms"))
              (when all-sent?
                (Thread/sleep sleep-time)
                (recur (+ i cases-to-gen))))))))
    quit-atom)
  :stop
  (reset! generator true))

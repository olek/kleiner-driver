(ns driver.store
  (:require [clojure.tools.logging :refer [info]]
            [mount.core :refer [defstate]]))

(def ^:private window-size 10) ; secs

(def ^:private org-defaults
  {:sent-cases {:timeseries (list)
                :count 0}
   :predictions {:timeseries (list)
                 :count 0}
   :errors {:timeseries (list)
            :count 0}
   :timeouts {:timeseries (list)
              :count 0}
   :target-rate 0}) ; target-rate is cases per second to generate

(def ^:private defaults
  {101 org-defaults
   102 org-defaults
   103 org-defaults})

(defstate ^:private store
  :start
  (atom defaults))

(defn- assert-org-id [org-id]
  (assert (contains? @store org-id)
          (str "Org " org-id " is not in the store.")))

(defn- average [key-name org-id]
  (let [curr-time (quot (System/currentTimeMillis) 1000)
        window-end (- curr-time window-size)]
    (swap! store
           update-in
           [org-id key-name :timeseries]
           (partial drop-while (partial > window-end)))
    (-> (get-in @store [org-id key-name :timeseries])
        count
        (/ window-size))))

(defn- update-timeseries [timeseries]
  (let [curr-time (quot (System/currentTimeMillis) 1000)
        window-end (- curr-time window-size)]
    (->> curr-time
         list
         (concat timeseries)
         (drop-while (partial > window-end)))))

(defn- inc-count [key-name org-id]
  (assert-org-id org-id)
  (swap! store
         update-in
         [org-id key-name :count]
         inc)
  (swap! store
         update-in
         [org-id key-name :timeseries]
         update-timeseries))

(defn stats []
  (into {}
        (for [[org-id org-data] @store]
             [org-id (reduce (fn [acc n]
                               (update-in acc
                                          [n]
                                          (comp (partial merge {:rate (average n org-id)}) dissoc)
                                          :timeseries))
                             org-data
                             [:sent-cases :predictions :errors :timeouts])])))

(defn inc-sent-cases-count [org-id]
  (inc-count :sent-cases org-id))

(defn inc-predictions-count [org-id]
  (inc-count :predictions org-id))

(defn inc-errors-count [org-id]
  (inc-count :errors org-id))

(defn inc-timeouts-count [org-id]
  (inc-count :timeouts org-id))

(defn target-rate [org-id]
  (assert-org-id org-id)
  (get-in @store [org-id :target-rate] 0))

(defn set-target-rate [rate org-id]
  (assert-org-id org-id)
  (assert (not (neg? rate)))
  (assert (<= rate 100))
  (swap! store
         update-in
         [org-id :target-rate]
         (constantly rate)))

(defn org-ids []
  (keys @store))

(defn reset []
  (reset! store defaults))

;; convenience method for REPL, not for production use
(defn pulse
  ([] (pulse 3 1))
  ([duration rate]
   (doseq [id (org-ids)]
     (set-target-rate rate id))
   (future (Thread/sleep (* duration 1000))
           (doseq [id (org-ids)]
             (set-target-rate 0 id)))))

(ns driver.store
  (:require [amalloy.ring-buffer :refer [ring-buffer]]
            [clojure.core.async :refer [thread]]
            [clojure.tools.logging :refer [info]]
            [mount.core :refer [defstate]]))

(def ^:private window-size 3) ; secs
(def ^:private max-target-rate 100000)
(def ^:private hundred-percent-target-rate 1000)
(def ^:private timeseries-buffer (ring-buffer (* window-size max-target-rate)))
(def ^:private recent-buffer (ring-buffer 5))

(def ^:private org-defaults
  {:sent-cases {:timeseries timeseries-buffer
                :count 0}
   :predictions {:timeseries timeseries-buffer
                 :recent recent-buffer
                 :count 0}
   :errors {:timeseries timeseries-buffer
            :count 0}
   :timeouts {:timeseries timeseries-buffer
              :count 0}
   :target-rate 0}) ; target-rate is cases per second to generate

(def ^:private defaults
  {1 org-defaults})

(defstate ^:private store
  :start
  (atom defaults))

(defn- assert-org-id [org-id]
  (assert (contains? @store org-id)
          (str "Org " org-id " is not in the store.")))

(defn- average [key-name org-id]
  (let [curr-time (quot (System/currentTimeMillis) 1000)
        window-end (- curr-time window-size)]
    (-> (get-in @store [org-id key-name :timeseries])
        (->> (drop-while (partial > window-end)))
        count
        (/ window-size))))

(defn- update-timeseries [timeseries]
  (let [curr-time (quot (System/currentTimeMillis) 1000)
        window-end (- curr-time window-size)]
    (conj timeseries curr-time)))

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
          [org-id (conj (reduce (fn [acc n]
                                  (update-in acc
                                             [n]
                                             (comp (partial merge {:rate (average n org-id)}) dissoc)
                                             :timeseries
                                             :recent))
                                org-data
                                [:sent-cases :predictions :errors :timeouts])
                        [:target-rate-percentage (-> (get-in @store [org-id :target-rate])
                                                     (/ hundred-percent-target-rate)
                                                     (* 100))])])))

(defn inc-sent-cases-count [org-id]
  (inc-count :sent-cases org-id))

(defn inc-predictions-count [{org-id :org :as case-data} prediction]
  (inc-count :predictions org-id)
  (swap! store
         update-in
         [org-id :predictions :recent]
         conj
         [case-data prediction]))

(defn inc-errors-count [org-id]
  (inc-count :errors org-id))

(defn inc-timeouts-count [org-id]
  (inc-count :timeouts org-id))

(defn recent-responses []
  (into {}
        (for [[org-id org-data] @store]
             [org-id (into []
                           (get-in org-data [:predictions :recent]))])))

(defn target-rate [org-id]
  (assert-org-id org-id)
  (get-in @store [org-id :target-rate] 0))

(defn set-target-rate [rate org-id]
  (assert-org-id org-id)
  (assert (not (neg? rate)))
  (assert (<= rate max-target-rate))
  (swap! store
         update-in
         [org-id :target-rate]
         (constantly rate)))

(defn set-target-rate-percentage [rate org-id]
  (assert-org-id org-id)
  (assert (not (neg? rate)))
  (assert (<= rate 100))
  (swap! store
         update-in
         [org-id :target-rate]
         (constantly (int (* (/ rate 100)
                             hundred-percent-target-rate)))))

(defn org-ids []
  (keys @store))

(defn reset []
  (reset! store defaults))

(defn pulse
  ([] (pulse 3 1))
  ([duration rate]
   (doseq [id (org-ids)]
     (set-target-rate rate id))
   (future (Thread/sleep (* duration 1000))
           (doseq [id (org-ids)]
             (set-target-rate 0 id)))
   [duration rate]))

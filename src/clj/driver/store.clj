(ns driver.store
  (:require [clojure.tools.logging :refer [info]]
            [mount.core :refer [defstate]]))

(def ^:private defaults
  {123 {:sent-cases-count 0
        :predictions-count 0
        :target-rate 0}}) ; target-rate is cases per second to generate

(defstate ^:private store
  :start
  (atom defaults))

(defn- assert-org-id [org-id]
  (assert (contains? @store org-id)
          (str "Org " org-id " is not in the store.")))

;; temporary function
(defn stats []
  @store)

(defn inc-sent-cases-count [org-id]
  (assert-org-id org-id)
  (swap! store
         update-in
         [org-id :sent-cases-count]
         inc))

(defn inc-predictions-count [org-id]
  (assert-org-id org-id)
  (swap! store
         update-in
         [org-id :predictions-count]
         inc))

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
           (reset))))

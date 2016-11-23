(ns driver.store
  (:require [clojure.tools.logging :refer [info]]
            [mount.core :refer [defstate]]))

(defstate ^:private store
  :start
  (atom {123 {:sent-cases-count 0
              :predictions-count 0
              :target-rate 0}}))

;; temporary function
(defn stats []
  @store)

(defn inc-sent-cases-count [org-id]
  (swap! store
         update-in
         [org-id :sent-cases-count]
         inc))

(defn inc-predictions-count [org-id]
  (swap! store
         update-in
         [org-id :predictions-count]
         inc))

(defn target-rate [org-id]
  (get-in @store [org-id :target-rate] 0))

(defn set-target-rate [rate org-id]
  (swap! store
         update-in
         [org-id :target-rate]
         (constantly rate)))

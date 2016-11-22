(ns driver.store
  (:require [clojure.tools.logging :refer [info]]
            [mount.core :refer [defstate]]))

(defstate ^:private store
  :start
  (atom {"org-123" {:sent-cases-count 0
                    :predictions-count 0}}))

(defn inc-sent-cases-count [org-id]
  (swap! store
         update-in
         [(str "org-" org-id) :sent-cases-count]
         inc))

(defn inc-predictions-count [org-id]
  (swap! store
         update-in
         [(str "org-" org-id) :predictions-count]
         inc))

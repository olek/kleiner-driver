(ns driver.processor
  (:require [clojure.tools.logging :refer [debug]]
            [clojure.core.async :refer [go-loop <!]]
            [driver.channels :refer [channels]]
            [driver.store :as store]
            [mount.core :refer [defstate]]))

(defn- process [[event-type {org-id :org} prediction]]
  (case event-type
    :start
    (store/inc-sent-cases-count org-id)

    :finish
    (case prediction
      :error (store/inc-errors-count org-id)
      :timeout (store/inc-timeouts-count org-id)
      (store/inc-predictions-count org-id))))

(defstate ^:private processor
  :start
  (let [stats-chan (:stats channels)
        quit-atom (atom false)]
    (go-loop []
      (when-not @quit-atom
        (when-let [stats-data (<! stats-chan)]
          (debug "Received stats" stats-data)
          (process stats-data)
          (recur))))
    quit-atom)
  :stop
  (reset! processor true))

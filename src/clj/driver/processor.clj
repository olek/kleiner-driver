(ns driver.processor
  (:require [clojure.tools.logging :refer [info]]
            [clojure.core.async :refer [go-loop <!]]
            [driver.channels :refer [channels]]
            [driver.store :as store]
            [mount.core :refer [defstate]]))

(defn- process [[event-type {:keys [org-id]} prediction]]
  (case event-type
    :start
    (store/inc-sent-cases-count org-id)

    :finish
    (store/inc-predictions-count org-id)))

(defstate ^:private processor
  :start
  (let [stats-chan (:stats channels)
        quit-atom (atom false)]
    (info "Waiting for stats")
    (go-loop []
      (when-not @quit-atom
        (when-let [stats-data (<! stats-chan)]
          (info "Received stats" stats-data)
          (process stats-data)
          (recur))))
    quit-atom)
  :stop
  (reset! processor true))

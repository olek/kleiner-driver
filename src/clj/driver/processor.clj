(ns driver.processor
  (:require [clojure.tools.logging :refer [info]]
            [clojure.core.async :refer [go-loop <!]]
            [driver.channels :refer [channels]]
            [mount.core :refer [defstate]]))

(defstate ^:private processor
  :start
  (let [stats-chan (:stats channels)
        quit-atom (atom false)]
    (info "Waiting for stats")
    (go-loop []
      (when-not @quit-atom
        (when-let [stats-data (<! stats-chan)]
          (info "Received stats" stats-data)
          (recur))))
    quit-atom)
  :stop
  (reset! processor true))

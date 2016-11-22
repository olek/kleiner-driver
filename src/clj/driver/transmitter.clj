(ns driver.transmitter
  (:require [clojure.tools.logging :refer [info]]
            [clojure.core.async :refer [go-loop <!]]
            [driver.channels :refer [channels]]
            [mount.core :refer [defstate]]))

(defn- transmit [data]
  (info "Received sample case" data))

(defstate ^:private transmitter
  :start
  (let [generated-cases-chan (:generated-cases channels)]
    (info "Waiting for the sample cases")
    (go-loop []
      (let [data (<! generated-cases-chan)]
        (when data
          (transmit data)
          (recur))))))

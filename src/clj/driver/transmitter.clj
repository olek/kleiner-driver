(ns driver.transmitter
  (:require [clojure.tools.logging :refer [info]]
            [clojure.core.async :refer [thread <!!]]
            [driver.channels :refer [channels]]
            [mount.core :refer [defstate]]))

(defn- transmit [data thread-num]
  (info "Transmitting sample case in thread" thread-num data))

(defstate ^:private transmitter
  :start
  (let [generated-cases-chan (:generated-cases channels)]
    (info "Waiting for the sample cases")
    (doseq [n (range 10)]
      (thread
        (loop []
          (let [data (<!! generated-cases-chan)]
            (when data
              (transmit data n)
              (recur))))))))

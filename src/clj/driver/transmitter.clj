(ns driver.transmitter
  (:require [clojure.tools.logging :refer [info]]
            [clojure.core.async :refer [thread <!!]]
            [driver.channels :refer [channels]]
            [mount.core :refer [defstate]]))

(defn- transmit [data thread-num]
  (info "Transmitting sample case in thread" thread-num data)
  (Thread/sleep 10000))

(defstate ^:private transmitter
  :start
  (let [generated-cases-chan (:generated-cases channels)
        quit-atom (atom false)]
    (info "Waiting for the sample cases")
    (doseq [n (range 10)]
      (thread
        (loop []
          (when-not @quit-atom
            (when-let [data (<!! generated-cases-chan)]
              (transmit data n)
              (recur))))))
    quit-atom)
  :stop
  (reset! transmitter true))

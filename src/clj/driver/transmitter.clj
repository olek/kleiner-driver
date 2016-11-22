(ns driver.transmitter
  (:require [clojure.tools.logging :refer [info]]
            [clojure.core.async :refer [thread <!! >!!]]
            [driver.channels :refer [channels]]
            [mount.core :refer [defstate]]))

(defn- transmit [data thread-num]
  (info "Transmitting sample case in thread" thread-num data)
  (Thread/sleep 10000)
  [data 42])

(defn- transmit-start-event [data ch]
  (>!! ch [:start data])
  data)

(defn- transmit-finish-event [[case-data prediction] ch]
  (>!! ch [:finish case-data prediction])
  prediction)

(defstate ^:private transmitter
  :start
  (let [generated-cases-chan (:generated-cases channels)
        stats-chan (:stats channels)
        quit-atom (atom false)]
    (info "Waiting for the sample cases")
    (doseq [n (range 10)]
      (thread
        (loop []
          (when-not @quit-atom
            (when-let [case-data (<!! generated-cases-chan)]
              (-> case-data
                  (transmit-start-event stats-chan)
                  (transmit n)
                  (transmit-finish-event stats-chan))
              (recur))))))
    quit-atom)
  :stop
  (reset! transmitter true))

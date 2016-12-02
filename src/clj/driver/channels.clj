(ns driver.channels
  (:require [clojure.tools.logging :refer [info]]
            [clojure.core.async :refer [chan close! dropping-buffer]]
            [mount.core :refer [defstate]]))

(defstate channels
  :start
  {:generated-cases (chan (dropping-buffer 100000))
   :stats (chan)}
  :stop
  (doseq [ch (vals channels)]
    (close! ch)))

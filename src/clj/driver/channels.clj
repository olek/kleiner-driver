(ns driver.channels
  (:require [clojure.tools.logging :refer [info]]
            [clojure.core.async :refer [chan close! dropping-buffer]]
            [mount.core :refer [defstate]]))

(defstate channels
  :start
  {:generated-cases (chan 10000)
   :stats (chan 10000)}
  :stop
  (doseq [ch (vals channels)]
    (close! ch)))

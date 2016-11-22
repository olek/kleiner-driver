(ns driver.channels
  (:require [clojure.tools.logging :refer [info]]
            [clojure.core.async :refer [chan close!]]
            [mount.core :refer [defstate]]))

(defstate channels
  :start
  {:generated-cases (chan)}
  :stop
  (doseq [ch (vals channels)]
    (close! ch)))

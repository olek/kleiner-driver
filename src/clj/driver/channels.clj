(ns driver.channels
  (:require [clojure.tools.logging :refer [info]]
            [clojure.core.async :refer [chan dropping-buffer close!]]
            [mount.core :refer [defstate]]))

(defstate channels
  :start
  {:generated-cases (chan (dropping-buffer 10))}
  :stop
  (doseq [ch (vals channels)]
    (close! ch)))

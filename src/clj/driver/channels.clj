(ns driver.channels
  (:require [clojure.tools.logging :refer [info]]
            [clojure.core.async :refer [chan close! dropping-buffer]]
            [environ.core :refer [env]]
            [mount.core :refer [defstate]]))

(def ^:private generator-buffer-size
  (Integer. (or (:generator-buffer-size env)
                "10000")))

(def config {:generator-buffer-size generator-buffer-size})

(defstate channels
  :start
  {:generated-cases (chan generator-buffer-size)
   :stats (chan 100000)}
  :stop
  (doseq [ch (vals channels)]
    (close! ch)))

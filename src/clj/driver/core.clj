(ns driver.core
  (:require [clojure.core.async :refer [chan]]
            [driver.api :refer [enable-http-server]]
            [driver.generator :as generator-for-mount]
            [driver.transmitter :as transmitter-for-mount]
            [driver.processor :as processor-for-mount]
            [mount.core :as mount])
  (:import (java.util.concurrent CountDownLatch))
  (:gen-class))

(defn -main
  "Start whole shebang"
  [& args]
    (enable-http-server)

    (mount/start)

    ;(start-everything)

    (.await (CountDownLatch. 1)))

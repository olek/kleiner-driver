(ns driver.core
  (:require [clojure.core.async :refer [chan]]
            [driver.api :refer [enable-http-server]]
            [driver.generator :refer [start-generator]]
            [mount.core :as mount])
  (:import (java.util.concurrent CountDownLatch)))

(defn- start-everything []
  (start-generator (chan 10)))

(defn -main
  "Start whole shebang"
  [& args]
    (enable-http-server)

    (mount/start)

    (start-everything)

    (.await (CountDownLatch. 1)))

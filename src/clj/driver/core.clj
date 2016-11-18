(ns driver.core)

(ns postman.core
  (:require [mount.core :as mount])
  (:import (java.util.concurrent CountDownLatch)))

(defn -main
  "Start whole shebang"
  [& args]
    ;(enable-http-server)

    (mount/start)

    ;(start-everything)

    (.await (CountDownLatch. 1)))

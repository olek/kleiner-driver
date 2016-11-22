(ns driver.transmitter
  (:require [clojure.tools.logging :refer [info]]
            [clojure.core.async :refer [thread >!!]]
            [driver.channels :refer [channels]]
            [mount.core :refer [defstate]]))

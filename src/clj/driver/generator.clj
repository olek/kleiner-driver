(ns driver.generator
  (:require [clojure.tools.logging :refer [info]]
            [clojure.core.async :refer [thread >!!]]
            [driver.channels :refer [channels]]
            [mount.core :refer [defstate]]))

(def ^:private quit-generator? (atom false))

(defstate ^:private generator
  :start
  (thread
    (while (not @quit-generator?)
      (info "Generating sample case")
      (>!! (:generated-cases channels) {:org-id 123 :id 123 :description "foo"})
      (Thread/sleep 1000)))

  :stop
  (reset! quit-generator? true))

(ns driver.generator
  (:require [clojure.tools.logging :refer [info]]
            [clojure.core.async :refer [thread >!!]]
            [driver.channels :refer [channels]]
            [mount.core :refer [defstate]]))

(defstate ^:private generator
  :start
  (thread
    (let [generated-cases-chan (:generated-cases channels)
          data {:org-id 123 :id 123 :description "foo"}]
      (loop []
        (info "Generating sample case")
        (let [sent (>!! generated-cases-chan data)]
          (when sent
            (Thread/sleep 100)
            (recur)))))))

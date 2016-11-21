(ns driver.generator
  (:require [clojure.tools.logging :refer [info]]
            [clojure.core.async :refer [thread >!!]]))

(defn start-generator [generated-cases-chan]
  (thread
    (loop []
      (info "Generating sample case")
      (>!! generated-cases-chan {:org-id 123 :id 123 :description "foo"})
      (Thread/sleep 100)
      (recur))))

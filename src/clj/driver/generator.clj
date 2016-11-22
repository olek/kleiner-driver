(ns driver.generator
  (:require [clojure.tools.logging :refer [info]]
            [clojure.core.async :refer [thread >!!]]
            [driver.channels :refer [channels]]
            [mount.core :refer [defstate]]))

(defstate ^:private generator
  :start
  (let [quit-atom (atom false)]
    (thread
      (let [generated-cases-chan (:generated-cases channels)]
        (loop [i 0]
          (when-not @quit-atom
            (info "Generating sample case")
            (let [data {:org-id 123 :description "foo" :id i}
                  sent (>!! generated-cases-chan data)]
              (when sent
                (Thread/sleep 1000)
                (recur (inc i))))))))
    quit-atom)
  :stop
  (reset! generator true))

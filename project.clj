(defproject kleiner-driver "0.1.0-SNAPSHOT"
  :description "Backend to generate fake cases and send them to router, along with API to control it."
  :url "http://example.com/FIXME"
  :main driver.core
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [mount "0.1.10"]

                 ; logging
                 [org.clojure/tools.logging "0.3.1"]

                 [http-kit "2.1.19"]
                 [cheshire "5.5.0" :exclusions [com.fasterxml.jackson.core/jackson-core]]
                 [environ "1.0.2"] ]

  :plugins [[lein-environ "1.0.1"]
            [lein-var-file "0.3.1"]]

  :source-paths ["src/clj"]
  :java-source-paths ["src/java"]
  :jvm-opts ["-Duser.timezone=UTC"]
             ;"-Djavax.net.debug=ssl" ;good to enable when debugging ssl issues
  :repl-options {:init-ns dev}

  :profiles {:test {:env {:foo "BAR"}
                    :dependencies [[org.clojure/tools.namespace "0.2.11"]]}
             :uberjar {:uberjar-name "driver"
                       :aot [driver.core]}})

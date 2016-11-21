(defproject kleiner-driver "0.1.0-SNAPSHOT"
  :description "Backend to generate fake cases and send them to router, along with API to control it."
  :url "http://example.com/FIXME"
  :main driver.core
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [mount "0.1.10"]

                 ; logging
                 [org.clojure/tools.logging "0.3.1"]

                 [http-kit "2.2.0"]
                 [cheshire "5.6.3"]
                 [compojure "1.5.1"]
                 [environ "1.1.0"]]

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

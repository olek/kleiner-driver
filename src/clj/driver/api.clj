(ns driver.api
  (:require [cheshire.core :as json]
            [clojure.tools.logging :refer [info]]
            [environ.core :refer [env]]
            [mount.core :refer [defstate]]
            [org.httpkit.server :refer :all]))

(defn status
  "Returns map of status checks for all services"
  [_]
  (let []
    {:status  200
     :headers {"Content-Type" "text/html"}
     :body    (json/generate-string {:foo       "BAR"})}))

(def ^:private http-server-enabled? (atom false))

(defn enable-http-server []
  (reset! http-server-enabled? true))

  (defstate ^:private server
    :start
    (when @http-server-enabled?
      (info "Starting kleiner-driver api" {:port 8080})
      (run-server status {:port 8080}))
    :stop
    (when @http-server-enabled?
      (server :timeout 100)))

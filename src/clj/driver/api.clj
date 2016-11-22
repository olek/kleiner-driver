(ns driver.api
  (:require [cheshire.core :as json]
            [clojure.tools.logging :refer [info]]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [driver.store :as store]
            [environ.core :refer [env]]
            [mount.core :refer [defstate]]
            [org.httpkit.server :refer :all]))

(defroutes app
  (GET "/foo1" [] (json/generate-string {:foo1 "BAR"}))
  (GET "/foo2" [] (json/generate-string {:foo2 "BAR"}))
  (GET "/stats" [] (json/generate-string (store/stats)))
  (route/not-found (json/generate-string {:error "Not Found"})))

(def ^:private http-server-enabled? (atom false))

(defn enable-http-server []
  (reset! http-server-enabled? true))

  (defstate ^:private server
    :start
    (when @http-server-enabled?
      (info "Starting kleiner-driver api" {:port 8080})
      (run-server app {:port 8080}))
    :stop
    (when @http-server-enabled?
      (server :timeout 100)))

(ns driver.api
  (:require [cheshire.core :as json]
            [clojure.tools.logging :refer [info]]
            [compojure.core :refer [defroutes GET POST]]
            [compojure.coercions :refer [as-int]]
            [compojure.route :as route]
            [clojure.string :as s]
            [driver.store :as store]
            [driver.transmitter :as transmitter]
            [environ.core :refer [env]]
            [mount.core :refer [defstate]]
            [org.httpkit.server :refer [run-server]]
            [ring.middleware.params :refer [wrap-params]]))

(def ^:private port (Integer. (or (:api-port env)
                                  (:nomad-port-http env)
                                  "8080")))
(def ^:private threadpool-size (Integer. (or (:api-threadpool-size env)
                                             "15")))

(defroutes routes
  (POST "/prediction-stub" []
    (Thread/sleep 30); prediction analysis is supposed to take some time
    (json/generate-string {:score 42}))
  (GET "/stats" [] (json/generate-string (store/stats)))
  (GET "/health" [] (json/generate-string {:healthy (not (nil? (store/recent-responses)))}))
  (GET "/recent" [] (json/generate-string (store/recent-responses)))
  (GET "/config" [] (json/generate-string (merge transmitter/config
                                                 {:api-port (:api-port env)
                                                  :api-threadpool-size (:api-threadpool-size env)})))
  (POST "/set-target-rate" [rate :<< as-int org :<< as-int]
    (store/set-target-rate rate org)
    {:status 204})
  (POST "/set-target-rate-percentage" [rate :<< as-int org :<< as-int]
    (store/set-target-rate-percentage rate org)
    {:status 204})
  (POST "/pulse" [duration :<< as-int rate :<< as-int]
    (store/pulse duration rate)
    {:status 204})
  (POST "/reset" []
    (store/reset)
    {:status 204})
  (route/not-found (json/generate-string {:error "Not Found"})))


;; Request logging
(defn- log-request [req resp resp-time]
  (info (format "Responded to %s \"%s\" with %s in %sms"
                (-> req :request-method name s/upper-case)
                (cond-> (:uri req)
                  (seq (:query-string req)) (str "?" (:query-string req)))
                (:status resp)
                resp-time)))

(defn- silence-request? [{:keys [uri request-method]}]
  (or (= uri "/health")
      (= uri "/prediction-stub")))

(defn wrap-log-request [handler]
  (fn [req]
    (if (silence-request? req)
      (handler req)
      (do
        (let [start (System/currentTimeMillis)
              response (handler req)
              resp-time (- (System/currentTimeMillis) start)]
          (log-request req response resp-time)
          response)))))

(def app (-> routes
             wrap-params
             wrap-log-request))

(def ^:private http-server-enabled? (atom false))

(defn enable-http-server []
  (reset! http-server-enabled? true))

(defstate ^:private server
  :start
  (when @http-server-enabled?
    (info "Starting kleiner-driver api" {:port port})
    (run-server app {:port port
                     :thread threadpool-size})) ;; increased from default 4 to help with prediction-stub
  :stop
  (when @http-server-enabled?
    (server :timeout 100)))

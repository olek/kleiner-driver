(ns driver.transmitter
  (:require [cheshire.core :as json]
            [clojure.core.async :refer [<!! >!! thread go]]
            [clojure.tools.logging :refer [info warn]]
            [driver.channels :refer [channels]]
            [environ.core :refer [env]]
            [mount.core :refer [defstate]]
            [clj-http.client :as http]
            [org.httpkit.client :as old-http])
  (:import com.fasterxml.jackson.core.JsonParseException
           java.net.SocketTimeoutException))

(def ^:private socket-timeout 3000) ; in ms

(def ^:private wait-for-pending-reduction 1) ; in ms
(def ^:private wait-for-slow-harvesting 1000) ; in ms
(def ^:private max-transmissions-to-log 10000) ; in ms

(def ^:private max-target-connections (Integer. (or (:max-target-connections env)
                                                    "1000"))) ; in ms
(def ^:private method (or (:target-http-method env)
                          "http"))
(def ^:private host (or (:target-host env)
                        "localhost"))
(def ^:private port (or (:target-port env)
                        "8080"))
(def ^:private path (or (:target-path env)
                        "/prediction-stub"))

(def ^:private threadpool-size (Integer. (or (:driver-threadpool-size env)
                                             "10")))

(def config {:max-target-connections max-target-connections
             :target-http-method method
             :target-host host
             :target-port port
             :target-path path})

;; BTW - by default, http-kit keeps idle connections for 120s

(def ^:private url (str method "://" host ":" port path))

;(def ^:private pending (atom []))

;(defn- transmit-raw [data]
;  (http/post url
;              {:body (json/generate-string data)
;               :timeout socket-timeout
;               :keepalive 1000 ; lets try to close connection quickly when not used
;               :headers {"Content-Type" "application/json"
;                         "Accept" "application/json"}}))

;(defn- transmit [generated-cases-chan stats-chan]
;  (when-let [data (poll! generated-cases-chan)]
;    ;; Turn off logging after first 10000 cases to improve performance
;    (when (< (:case data) max-transmissions-to-log)
;      (info "Transmitting sample case" (:case data) "for org" (:org data) data))
;    (put! stats-chan [:start data])
;    [data (transmit-raw data)]))


;(defn- parse-and-report [data response ch]
;  (go (let [error (:error response)
;            prediction (when-not error
;                         (try
;                           (-> response
;                               :body
;                               json/parse-string
;                               (get "score"))
;                           (catch JsonParseException e nil)))
;            prediction (cond
;                         (and (nil? error) (number? prediction)) prediction
;                         (instance? TimeoutException error) :timeout
;                         :else :error)
;            _ (when (= prediction :error)
;                (warn "Received error:" (or error
;                                            response)))]

;        (>! ch [:finish data prediction]))))

;(defn- harvest-pending [stats-chan]
;  (locking pending
;    (let [{done true not-done false} (group-by (comp realized? second) @pending)]
;      (reset! pending not-done)
;      ;(when (seq done)
;      ;  (go (info "Harvested" (count done) "http results, left" (count not-done) "for later")))
;      (doseq [[data prom] done]
;        (parse-and-report data @prom stats-chan))
;      (seq done))))

;(defn- add-to-pending [stats-chan producer-fn]
;  (letfn [(attempt []
;            (when (<= (count @pending) max-target-connections)
;              (when-let [res (producer-fn stats-chan)]
;                (swap! pending conj res)))
;            )]
;    (locking pending
;      (or (attempt)
;          (and
;            (harvest-pending stats-chan)
;            (attempt))))))

;(defstate ^:private transmitter
;  :start
;  (let [generated-cases-chan (:generated-cases channels)
;        stats-chan (:stats channels)
;        quit-atom (atom false)]

;    ;; Collects results of http calls when traffic is low
;    (thread
;      (loop []
;        (when-not @quit-atom
;          (harvest-pending stats-chan)
;          (Thread/sleep wait-for-slow-harvesting)
;          (recur))))

;    (thread
;        (loop []
;          (when-not @quit-atom
;            (when-not (add-to-pending stats-chan (partial transmit generated-cases-chan))
;              (Thread/sleep wait-for-pending-reduction))
;            (recur))))
;    quit-atom)
;  :stop
;  (reset! transmitter true))

(defn- response-callback [data response]
  (let [ch (:stats channels)
        status (:status response)
        error (when-not (<= 200 status 299 )
                (str status ": " (:body response)))
        prediction (when-not error
                     (try
                       (-> response
                           :body
                           json/parse-string
                           (get "score"))
                       (catch JsonParseException e nil)))
        prediction (if (and (nil? error) (number? prediction))
                     prediction
                     :error)
        _ (when (= prediction :error)
            (warn "Received error:" (or error
                                        response)))]

    (>!! ch [:finish data prediction])))

(defn- error-callback [data ex]
  (warn "Received exception" ex)
  (>!! (:stats channels) [:finish data (if (instance? SocketTimeoutException ex)
                                        :timeout
                                        :error)]))

(defn- transmit [data]
  ;; Turn off logging after first 10000 cases to improve performance
  (when (< (:case data) max-transmissions-to-log)
    (info "Transmitting sample case" (:case data) "for org" (:org data) data))
  (>!! (:stats channels) [:start data])
  (http/post url
             {:async? true
              ;              :as :json-strict-string-keys
              :body (json/generate-string data)
              :conn-timeout socket-timeout
              :socket-timeout socket-timeout
              :content-type :json
              :accept :json}
             (partial response-callback data)
             (partial error-callback data)))


(defstate ^:private transmitter
  :start
  (let [generated-cases-chan (:generated-cases channels)
        stats-chan (:stats channels)
        quit-atom (atom false)]

    (thread
      (http/with-async-connection-pool {:timeout 5 :threads max-target-connections :insecure? false :default-per-route max-target-connections}
        (loop []
          (when-not @quit-atom
            (when-let [data (<!! generated-cases-chan)]
              (transmit data)
              (recur))))))

    (thread
      (http/with-async-connection-pool {:timeout 5 :threads max-target-connections :insecure? false :default-per-route max-target-connections}
        (loop []
          (when-not @quit-atom
            (when-let [data (<!! generated-cases-chan)]
              (transmit data)
              (recur))))))
    quit-atom)
  :stop
  (reset! transmitter true))

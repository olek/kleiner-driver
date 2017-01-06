(ns driver.transmitter
  (:require [cheshire.core :as json]
            [clojure.core.async :refer [<! <!! >! >!! thread go poll! offer! timeout]]
            [clojure.tools.logging :refer [info warn]]
            [driver.channels :refer [channels]]
            [environ.core :refer [env]]
            [mount.core :refer [defstate]]
            [org.httpkit.client :as http])
  (:import com.fasterxml.jackson.core.JsonParseException
           org.httpkit.client.TimeoutException))

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

(def ^:private pending (atom []))

(defn- >!-verbose [ch-name payload]
  (let [ch (get channels ch-name)]
    (when-not (offer! ch payload)
      (info (name ch-name) "channel is full")
      (>! ch payload))))

(defn- >!!-verbose [ch-name payload]
  (let [ch (get channels ch-name)]
    (when-not (offer! ch payload)
      (info (name ch-name) "channel is full")
      (>!! ch payload))))


(defn- transmit-raw-fake [data]
  (let [prom (promise)]
    (go
      (<! (timeout 30))
      (>!-verbose :responses [data {:body "{\"score\":42}"}])
      (deliver prom {}))
    prom))
  ;(future
  ;  (Thread/sleep 30)
  ;  (<! (timeout 1000))
  ;  (>!! (:responses channels) [data {:body "{\"score\":42}"}])))

(defn- transmit-raw [data]
  (http/post url
             {:body (json/generate-string data)
              :timeout socket-timeout
              :keepalive 1000 ; lets try to close connection quickly when not used
              :headers {"Content-Type" "application/json"
                        "Accept" "application/json"}}
             (fn [response]
               (>!!-verbose :responses [data response])
               response)))

(defn- transmit []
  (let [generated-cases-chan (:generated-cases channels)
        data (poll! generated-cases-chan)]
    (when data
      ;; Turn off logging after first 10000 cases to improve performance
      (when (< (:case data) max-transmissions-to-log)
        (info "Transmitting sample case" (:case data) "for org" (:org data) data))
      (>!!-verbose :stats [:start data])
      [data (transmit-raw data)])))


(defn- parse-and-report [data response]
  (let [error (:error response)
        prediction (when-not error
                     (try
                       (-> response
                           :body
                           json/parse-string
                           (get "score"))
                       (catch JsonParseException e nil)))
        prediction (cond
                     (and (nil? error) (number? prediction)) prediction
                     (instance? TimeoutException error) :timeout
                     :else :error)
        _ (when (= prediction :error)
            (warn "Received error:" (or error
                                        response)))]

    (>!!-verbose :stats [:finish data prediction])))

(defn- harvest-pending []
  (locking pending
    (let [{done true not-done false} (group-by (comp realized? second) @pending)]
      (reset! pending not-done)
      ;(when (seq done)
      ;  (go (info "Harvested" (count done) "http results, left" (count not-done) "for later")))
      (seq done))))

(defn- add-to-pending []
  (letfn [(attempt []
            (when (<= (count @pending) max-target-connections)
              (when-let [res (transmit)]
                (swap! pending conj res)))
            )]
    (locking pending
      (or (attempt)
          (and
            (harvest-pending)
            (attempt))))))

(defstate ^:private transmitter
  :start
  (let [generated-cases-chan (:generated-cases channels)
        responses-chan (:responses channels)
        quit-atom (atom false)]

    (thread
      (loop []
        (when-not @quit-atom
          (when-not (add-to-pending)
            (<!! (timeout wait-for-pending-reduction)))
          (recur))))

    (thread
      (loop []
        (when-not @quit-atom
          (when-let [[data response] (<!! responses-chan)]
            (parse-and-report data response)
            (recur)))))
    quit-atom)
  :stop
  (reset! transmitter true))

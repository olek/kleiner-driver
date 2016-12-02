(ns driver.transmitter
  (:require [cheshire.core :as json]
            [clojure.core.async :refer [<!! >!! thread]]
            [clojure.tools.logging :refer [info]]
            [driver.channels :refer [channels]]
            [environ.core :refer [env]]
            [mount.core :refer [defstate]]
            [org.httpkit.client :as http])
  (:import com.fasterxml.jackson.core.JsonParseException
           org.httpkit.client.TimeoutException))

(def ^:private timeout 3000) ; in ms

(def ^:private method (or (:target-http-method env)
                          "http"))
(def ^:private host (or (:target-host env)
                        "localhost"))
(def ^:private port (or (:target-port env)
                        "8080"))
(def ^:private path (or (:target-path env)
                        "/prediction-stub"))

(def ^:private threadpool-size (Integer. (or (:threadpool-size env)
                                             "10")))

(info "Environment" {:threadpool-size threadpool-size
                     :target-http-method method
                     :target-host host
                     :target-port port
                     :target-path path})

;; BTW - by default, http-kit keeps idle connections for 120s

(def ^:private url (str method "://" host ":" port path))

(defn- transmit-raw [data]
  @(http/post url
              {:body (json/generate-string data)
               :timeout timeout
               :headers {"Content-Type" "application/json"
                         "Accept" "application/json"}}))

(defn- transmit [data thread-id]
  ;; Turn off logging after first 1000 cases to improve performance
  (when (< (:case data) 1000)
    (info "Transmitting sample case " (:case data) "for org" (:org data) " in thread" thread-id data))
  (let [response (transmit-raw data)
        error (:error response)
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
                     :else :error)]
    [data prediction]))

(defn- transmit-start-event [data ch]
  (>!! ch [:start data])
  data)

(defn- transmit-finish-event [[case-data prediction] ch]
  (>!! ch [:finish case-data prediction])
  prediction)

(defstate ^:private transmitter
  :start
  (let [generated-cases-chan (:generated-cases channels)
        stats-chan (:stats channels)
        quit-atom (atom false)]
    (doseq [n (range threadpool-size)]
      (thread
        (loop []
          (when-not @quit-atom
            (when-let [case-data (<!! generated-cases-chan)]
              (-> case-data
                  (transmit-start-event stats-chan)
                  (transmit n)
                  (transmit-finish-event stats-chan))
              (recur))))))
    quit-atom)
  :stop
  (reset! transmitter true))

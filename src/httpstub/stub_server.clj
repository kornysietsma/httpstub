(ns httpstub.stub-server
  (:import (java.util.concurrent.atomic AtomicInteger)
           (java.util Date))
  (:require [ring.adapter.jetty :as jetty]
            [cheshire.core :as cheshire]))

(defn with-slurped-body [request]
  (if (:body request)
    (assoc request :body (slurp (:body request)))
    request))

(def echo-handler
  (fn [request]
    {:status  200
     :headers {"Content-Type" "application/json"}
     :body    (cheshire/generate-string (with-slurped-body request))}))

(def fail-handler
  (fn [_]
    {:status 500
     :body   "Fail handler called!"}))

; state is keyed by port - no ports initially
(defonce server-state (atom {}))

(defn- conj-vec [v data]
  (if (nil? v)
    [data]
    (conj v data)))

{:server  nil
 :history {} ; map from conversation id to history
 :handler fail-handler
 }

(defn- add-history-to-state [state port conv-id kind data]
  (when-not (get state port)
    (throw (Exception. (str "Can't add history - no server at port " port))))
  (update-in state [port :history conv-id] conj-vec
             {:conversation conv-id
              :timestamp (.getTime (Date.))
              :kind      kind
              :data      data}))

(defn- add-history! [port conv-id kind data]
  (swap! server-state add-history-to-state port conv-id kind data))

(defonce atomic-id (AtomicInteger. 0))
(defn- next-id [] (.incrementAndGet atomic-id))

(defn- handler-middleware [port handler]
  (fn [request]
    (let [conv-id (next-id)]
      (add-history! port conv-id :request (with-slurped-body request))
      (try
        (let [response (handler request)]
          (add-history! port conv-id :response response)
          response)
        (catch Exception e
               (add-history! port conv-id :exception e))))))

(defn- handlerfn
  ([port request]
  ((get-in @server-state [port :handler]) request))
  ([port request & extras]
   (prn "wtf?" port request extras)
   (handlerfn port request)))

(defn stub-handler [port]
  (->> (partial handlerfn port)
       (handler-middleware port)))

(defn start-server [port]
  (when (get-in @server-state [port :server])
    (throw (Exception. (str "Server started twice on port " port))))
  (let [server (jetty/run-jetty (stub-handler port) {:port port :join? false})]
    (swap! server-state
           #(assoc % port {:server server
                           :history {}
                           :handler fail-handler}))))

(defn stop-server [port]
  (if-let [server (get-in @server-state [port :server])]
    (do
      (.stop server)
      (swap! server-state #(assoc-in % [port :server] nil)))))

(defn set-handler! [port handler]
  (when-not (get-in @server-state [port :server])
    (throw (Exception. (str "No server at port " port))))
  (swap! server-state #(assoc-in % [port :handler] handler)))

(defn reset-handler! [port]
  (set-handler! port fail-handler))

(defn reset-history! [port]
  (when-not (get-in @server-state [port :server])
    (throw (Exception. (str "No server at port " port))))
  (swap! server-state #(assoc-in % [port :history] {})))

(defn reset-all []
  (for [port (keys @server-state)]
    (do
      (stop-server port)
      (reset-handler! port)
      (reset-history! port))))

(defn history [port]
  (->> (get-in @server-state [port :history])
       (sort-by (fn [[id _]] id))
       (map second)))

(defn latest-conversation [port]
  (last (history port)))

(defn latest-history-of-kind [port kind]
  (let [h (latest-conversation port)
        matches (filter #(= (:kind %) kind) h)
        matchcount (count matches)]
    (if (= 1 matchcount)
      (first matches)
      (throw (Exception. (str "Expected 1 match of kind " kind " - saw " matchcount))))))

(defn latest-history-header
  ([port header] (latest-history-header port :response header))
  ([port kind header]
   (-> (latest-history-of-kind port kind)
       :data
       :headers
       (get header))))

(defmacro with-server [port & body]
  `(try
     (start-server ~port)
     (println "Jetty server running on port " ~port)
     ~@body
     (finally
       (stop-server ~port)
       (println "Jetty server stopped"))))

(defmacro with-handler [port handler & body]
  `(try
     (do
       (reset-history! ~port)
       (set-handler! ~port ~handler)
       ~@body)
     (finally
       (reset-handler! ~port))))


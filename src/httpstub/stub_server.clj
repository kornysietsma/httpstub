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

(defonce server-state (atom {:server  nil
                             :history {} ; map from conversation id to history
                             :handler fail-handler
                             }))

(defn- conj-vec [v data]
  (if (nil? v)
    [data]
    (conj v data)))

(defn- add-history! [conv-id kind data]
  (swap! server-state #(update-in % [:history conv-id] conj-vec
                                  {:conversation conv-id
                                   :timestamp (.getTime (Date.))
                                   :kind      kind
                                   :data      data})))

(defonce atomic-id (AtomicInteger. 0))
(defn- next-id [] (.incrementAndGet atomic-id))

(defn- handler-middleware [handler]
  (fn [request]
    (let [conv-id (next-id)]
      (add-history! conv-id :request (with-slurped-body request))
      (try
        (let [response (handler request)]
          (add-history! conv-id :response response)
          response)
        (catch Exception e
               (add-history! conv-id :exception e))))))

(defn- handlerfn [request]
  ((:handler @server-state) request))

(def stub-handler
  (-> handlerfn
      handler-middleware))

(defn start-server [port]
  (when (:server @server-state)
    (throw (Exception. "Server started twice!")))
  (let [server (jetty/run-jetty stub-handler {:port port :join? false})]
    (swap! server-state #(assoc % :server server))))

(defn stop-server []
  (when (:server @server-state)
    (do
      (.stop (:server @server-state))
      (swap! server-state #(assoc % :server nil)))))

(defn set-handler! [handler]
  (swap! server-state #(assoc % :handler handler)))

(defn reset-handler! []
  (set-handler! fail-handler))

(defn reset-history! []
  (swap! server-state #(assoc % :history {})))

(defn reset-all []
  (stop-server)
  (reset-handler!)
  (reset-history!))

(defn history []
  (->> (:history @server-state)
       (sort-by (fn [[id _]] id))
       (map second)))

(defn latest-conversation []
  (last (history)))

(defn latest-history-of-kind [kind]
  (let [h (latest-conversation)
        matches (filter #(= (:kind %) kind) h)
        matchcount (count matches)]
    (if (= 1 matchcount)
      (first matches)
      (throw (Exception. (str "Expected 1 match of kind " kind " - saw " matchcount))))))

(defn latest-history-header
  ([header] (latest-history-header :response header))
  ([kind header]
   (-> (latest-history-of-kind kind)
       :data
       :headers
       (get header))))

(defmacro with-server [port & body]
  `(try
     (start-server ~port)
     (println "Jetty server running on port " ~port)
     ~@body
     (finally
       (stop-server)
       (println "Jetty server stopped"))))

(defmacro with-handler [handler & body]
  `(try
     (do
       (reset-history!)
       (set-handler! ~handler)
       ~@body)
     (finally
       (reset-handler!))))


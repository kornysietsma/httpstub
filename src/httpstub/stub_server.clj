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
                             :history []
                             :handler fail-handler
                             }))

(defn- add-history! [id kind data]
  (swap! server-state #(update-in % [:history] conj
                                  {:id        id
                                   :timestamp (.getTime (Date.))
                                   :kind      kind
                                   :data      data})))

(defonce atomic-id (AtomicInteger. 0))
(defn- next-id [] (.incrementAndGet atomic-id))

(defn- handler-middleware [handler]
  (fn [request]
    (let [id (next-id)]
      (add-history! id :request (with-slurped-body request))
      (try
        (let [response (handler request)]
          (add-history! id :response response)
          response)
        (catch Exception e
               (add-history! id :exception e))))))

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
  (swap! server-state #(assoc % :history [])))

(defn reset-all []
  (stop-server)
  (reset-handler!)
  (reset-history!))

(defn- conversation-kv-list-to-map [history]
  (for [[id data] history]
    {:id id :conversation (sort-by :timestamp data)}))

(defn history []
  (->> (:history @server-state)
       (group-by :id)
       (sort-by (fn [[id _]] id))
       (conversation-kv-list-to-map)))

(defn latest-history []
  (:conversation (last (history))))

(defn latest-history-of-kind [kind]
  (let [h (latest-history)
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


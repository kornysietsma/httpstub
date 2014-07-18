(ns httpstub.stub-server
  (:import (java.util.concurrent.atomic AtomicInteger)
           (java.util Date))
  (:require [ring.adapter.jetty :as jetty]
            [cheshire.core :as cheshire]))

(defn with-slurped-body [request]
  (if (:body request)
    (assoc request :body (slurp (:body request)))
    request))

(defn echo-handler [request]
    {:status  200
     :headers {"Content-Type" "application/json"}
     :body    (cheshire/generate-string (with-slurped-body request))})

(defn fail-handler [req]
    {:status 500
     :body   "Fail handler called!"})

; state is keyed by port - no ports initially
(defonce server-state (atom {}))

(defn- conj-vec [v data]
  (if (nil? v)
    [data]
    (conj v data)))

(comment "server state is a map by port to:"
{:server  "jetty server object"
 :history {
            "conv-id" { :conversation "conv-id"
                        :timestamp "ts"
                        :kind #{:request :response :exception}
                        :data "some sort of history data"}
            }
 :handlers (handlers ... fh) ; note - push a handler per with-handlers block, use compojure.core/routing to run them
 })

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
  [port request]
  (let [handlers (get-in @server-state [port :handlers])]
    (some #(% request) handlers)))

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
                           :handlers (list fail-handler)}))))

(defn stop-server [port]
  (if-let [server (get-in @server-state [port :server])]
    (do
      (.stop server)
      (swap! server-state #(assoc-in % [port :server] nil)))))

(defn set-handlers! [port handlers]
  (when-not (get-in @server-state [port :server])
    (throw (Exception. (str "No server at port " port))))
  (swap! server-state #(assoc-in % [port :handlers] handlers)))

(defn reset-handlers! [port]
  (set-handlers! port (list fail-handler)))

(defn push-handler! [port handler]
  (when-not (get-in @server-state [port :server])
    (throw (Exception. (str "No server at port " port))))
  (swap! server-state #(update-in % [port :handlers] (fn [h] (conj h handler)))))

(defn pop-handler! [port]
  (when-not (get-in @server-state [port :server])
    (throw (Exception. (str "No server at port " port))))
  (swap! server-state #(update-in % [port :handlers] rest)))

(defn reset-history! [port]
  (when-not (get-in @server-state [port :server])
    (throw (Exception. (str "No server at port " port))))
  (swap! server-state #(assoc-in % [port :history] {})))

(defn reset-all []
  (for [port (keys @server-state)]
    (do
      (stop-server port)
      (reset-handlers! port)
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
       (push-handler! ~port ~handler)
       ~@body)
     (finally
       (pop-handler! ~port))))


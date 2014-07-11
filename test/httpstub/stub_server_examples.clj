(ns httpstub.stub-server-examples
  (:require [midje.sweet :refer :all]
            [httpstub.stub-server :as stub]
            [compojure.core :refer [routes GET]]
            [clj-http.client :as http]))

(defn fix-http [response] "fix clj-http responses for midje - see https://github.com/marick/Midje/issues/269"
  (assoc response :headers (into {} (:headers response))))

(stub/with-server 3001
  (facts "about http requests"
    (fact "you can make a http request to a stub server"
      (stub/with-handler
        (routes
          (GET "/foo" [req]
               {:status 200 :body "hello"}))
        (let [foo-resp (fix-http (http/get "http://localhost:3001/foo"))]
          foo-resp => (contains {:status 200 :body "hello"})
          (count (stub/history)) => 1
          (count (stub/latest-conversation)) => 2
          (map :kind (stub/latest-conversation)) => [:request :response]
          )))
    (fact "you can add headers to check behaviour"
      (stub/with-handler
        (routes
          (GET "/foo/:id" [id]
               {:status 200 :body "hello" :headers {"test-data" id}}))
        (let [foo-resp (fix-http (http/get "http://localhost:3001/foo/123"))]
          ; can just check response
          foo-resp => (contains {:headers (contains {"test-data" "123"})})
          ; or if response is buried in api calls, can check history
          (stub/latest-history-header "test-data") => "123"
          )))))

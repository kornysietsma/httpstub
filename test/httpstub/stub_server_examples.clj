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
      (stub/with-handler 3001
        (routes
          (GET "/foo" [req]
               {:status 200 :body "hello"}))
        (let [foo-resp (fix-http (http/get "http://localhost:3001/foo"))]
          foo-resp => (contains {:status 200 :body "hello"})
          (count (stub/history 3001)) => 1
          (count (stub/latest-conversation 3001)) => 2
          (map :kind (stub/latest-conversation 3001)) => [:request :response]
          )))
    (fact "you can add headers to check behaviour"
      (stub/with-handler 3001
        (routes
          (GET "/foo/:id" [id]
               {:status 200 :body "hello" :headers {"test-data" id}}))
        (let [foo-resp (fix-http (http/get "http://localhost:3001/foo/123"))]
          ; can just check response
          foo-resp => (contains {:headers (contains {"test-data" "123"})})
          ; or if response is buried in api calls, can check history
          (stub/latest-history-header 3001 "test-data") => "123"
          )))))

(facts "about multiple servers"
  (stub/with-server 3001
    (stub/with-server 3002
      (stub/with-handler 3001
        (routes
          (GET "/foo" [req]
               {:status 200 :body "foo!"}))
        (stub/with-handler 3002
          (routes
            (GET "/bar" [req]
                 {:status 200 :body "bar!"}))
          (fact "you can get from both servers"
            (let [foo-resp (fix-http (http/get "http://localhost:3001/foo"))
                  bar-resp (fix-http (http/get "http://localhost:3002/bar"))
                  _ (fix-http (http/get "http://localhost:3001/foo"))]
              foo-resp => (contains {:status 200 :body "foo!"})
              bar-resp => (contains {:status 200 :body "bar!"})
              (count (stub/history 3001)) => 2
              (count (stub/history 3002)) => 1
              )))))))

(facts "about multiple handlers"
  (stub/with-server 3001
    (stub/with-handler 3001
      (GET "/foo" [req]
           {:status 200 :body "foo!"})
      (fact "outside nesting, unknown routes fail"
        (fix-http (http/get "http://localhost:3001/foo")) => (contains {:status 200 :body "foo!"})
        (fix-http (http/get "http://localhost:3001/bar")) => (throws Exception "clj-http: status 500"))
      (stub/with-handler 3001
        (GET "/bar" [req]
             {:status 200 :body "bar!"})
        (fact "inside nesting, new routes succeed"
          (fix-http (http/get "http://localhost:3001/foo")) => (contains {:status 200 :body "foo!"})
          (fix-http (http/get "http://localhost:3001/bar")) => (contains {:status 200 :body "bar!"})))
      (fact "after nesting, unknown routes fail"
        (fix-http (http/get "http://localhost:3001/foo")) => (contains {:status 200 :body "foo!"})
        (fix-http (http/get "http://localhost:3001/bar")) => (throws Exception "clj-http: status 500"))
      )))

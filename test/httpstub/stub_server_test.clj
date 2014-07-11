(ns httpstub.stub-server-test
  (:require [midje.sweet :refer :all]
            [httpstub.stub-server :as stub]
            [compojure.core :refer [routes GET]]
            [clj-http.client :as http]))


(fact "By default the server state is no server, a fail-all handler and no history"
  (do
    (stub/reset-all)
    (:server @stub/server-state) => nil
    (stub/history) => []
    ((:handler @stub/server-state) {}) => (contains {:status 500})))

(facts "about simple handling"
  (let
    [_ (stub/reset-all)
     next-id (inc (.get stub/atomic-id))
     response (stub/stub-handler {:orig :request})]
    (fact "The stub handler records request and response history"
      response => (contains {:status 500})
      (count (stub/history)) => 1
      (first (stub/history)) =>
      (just
        (contains {:conversation next-id
                   :kind :request
                   :data {:orig :request}})
        (contains {:conversation next-id
                   :kind :response
                   :data {:body "Fail handler called!", :status 500}})))
    (fact "you can get the latest conversation by kind"
      (stub/latest-history-of-kind :request)
      => (contains {:conversation next-id :kind :request}))))


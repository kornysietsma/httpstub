(ns httpstub.stub-server-test
  (:require [midje.sweet :refer :all]
            [httpstub.stub-server :as stub]
            [compojure.core :refer [routes GET]]
            [clj-http.client :as http]))

(stub/with-server 3001

  (fact "By default the server state is a fail-all handler and no history"
    (stub/history 3001) => [])

  (facts "about simple handling"
    (let
        [next-id (inc (.get stub/atomic-id))
         response ((stub/stub-handler 3001) {:orig :request})]
      (fact "The stub handler records request and response history"
        response => (contains {:status 500})
        (count (stub/history 3001)) => 1
        (first (stub/history 3001)) =>
        (just
          (contains {:conversation next-id
                     :kind         :request
                     :data         {:orig :request}})
          (contains {:conversation next-id
                     :kind         :response
                     :data         {:body "Fail handler called!", :status 500}}))
        )
      (fact "you can get the latest conversation by kind"
        (stub/latest-history-of-kind 3001 :request)
        => (contains {:conversation next-id :kind :request}))))

  )


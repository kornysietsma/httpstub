(ns httpstub.stub-server-examples
  (:require [midje.sweet :refer :all]
            [httpstub.stub-server :as stub]
            [compojure.core :refer [routes GET]]
            [clj-http.client :as http]))


(comment "ok, so I didn't TDD this, so sue me :)  Unit tests are on their way")
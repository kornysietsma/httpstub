(defproject httpstub "0.1.0-SNAPSHOT"
            :description "FIXME: write description"
            :url "http://example.com/FIXME"
            :license {:name "Eclipse Public License"
                      :url  "http://www.eclipse.org/legal/epl-v10.html"}
            :dependencies [[org.clojure/clojure "1.6.0"]
                           [clj-http "0.9.2"]
                           [compojure "1.1.8"]
                           [cheshire "5.3.1"]
                           [clj-time "0.7.0"]
                           [ring "1.3.0"]]

            :profiles {:dev {:dependencies [[midje "1.6.3"]]}}

            :plugins [[lein-ring "0.8.2"]
                      [lein-midje "3.1.1"]
                      [lein-pprint "1.1.1"]])

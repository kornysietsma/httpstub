# httpstub

A Clojure library designed to assist testing programs that need to talk to HTTP servers.

Basically this is some simple macros to spin up a local Jetty server with dynamic routes, that records all activity, so you can
write expressive tests easily.

This only works on localhost requests - if you need to stub out a real remote server, have a look at (clj-http-fake)[https://github.com/myfreeweb/clj-http-fake] which is far more powerful.  And complex!

This is very basic at the moment.  For instance, you can only run a single server!  Treat it as an alpha.

## Usage

Basically you can wrap all your tests in a `with-server` macro, which runs a Jetty server on a specified localhost port.

And then you wrap individual chunks of tests in a `with-handler` macro, which sets up a temporary Ring handler, typically a set of
Compojure routes, and then runs the macro body, recording any actual requests and responses (and exceptions!) that happen.

~~~
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
          )))
~~~

For more see stub_server_examples in the tests

## TODO

* unit tests!
* Add a not-handled check with a better error message
* Allow for multiple simultaneous jetty servers - need a local binding or something, rather than a single atom

## License

Copyright © 2014 Kornelis Sietsma

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

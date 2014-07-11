# httpstub

A Clojure library designed to assist testing programs that need to talk to HTTP servers.

Basically this is some simple macros to spin up a local Jetty server with dynamic routes, that records all activity, so you can
write expressive tests easily.

This only works on localhost requests - if you need to stub out a real remote server, have a look at [clj-http-fake](https://github.com/myfreeweb/clj-http-fake) which is far more powerful.  And more complex.

This is very basic at the moment.

## Usage

Basically you can wrap all your tests in a `with-server` macro, which runs a Jetty server on a specified localhost port.  You can have one server per port, and you need to specify the port when interacting with a server in any way.

Then you wrap individual chunks of tests in a `with-handler` macro, which sets up a temporary Ring handler, typically a set of
Compojure routes, and then runs the macro body, recording any actual requests and responses (and exceptions!) that happen.

~~~
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
          )))
~~~

For more see stub_server_examples in the tests

## TODO

* Add a not-handled check with a better error message
* Add some way to add events from routes - setting headers is ugly, but there needs to be a way to find the _current_ conversation id. Maybe it could be in the request?

## License

        DO WHAT THE FUCK YOU WANT TO PUBLIC LICENSE
                    Version 2, December 2004

 Copyright (C) 2014 Kornelis Sietsma <korny@sietsma.com>

 Everyone is permitted to copy and distribute verbatim or modified
 copies of this license document, and changing it is allowed as long
 as the name is changed.

            DO WHAT THE FUCK YOU WANT TO PUBLIC LICENSE
   TERMS AND CONDITIONS FOR COPYING, DISTRIBUTION AND MODIFICATION

  0. You just DO WHAT THE FUCK YOU WANT TO.

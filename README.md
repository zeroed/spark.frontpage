spark.frontpage
===============

Spark! Just a frontpage... 

A micro project to create a URL Shortener RESTful service.

## Technologies

- Java
- [Spark Java](http://sparkjava.com/) Micro Web framework.
- [Redis](http://redis.io/) Key value store.
- REST everywhere

## Well?

A couple of route makes the magic. Here the summary...

```java
get "/" // welcome
get "/protected" //just kidding
get "/redis" // add a key-value pair e.g. /redis?key=Foo&value=Bar
get "/redis/:key" // retrieve a value
get "/url/" // get a form
post "/url/new/" // read an URL
get "/url/go/:key" // put your key and get redirected!
```

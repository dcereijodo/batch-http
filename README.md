# BatchHttp [![CircleCI](https://circleci.com/gh/digitalorigin/batch-http.svg?style=svg&circle-token=d196d5b828e9e0debb5c25f04e7279c1f342d675)](https://circleci.com/gh/digitalorigin/batch-http)
A tool for processing HTTP request batches through a REST API. It reads the `stdin` for JSON lines representing HTTP requests,
converts each line to an HTTP request and makes the request, providing both the request and the response as an output in the `stdout`.

For example, when passed a JSON string such as
```json
 {
  "request": {
    "method": "GET",
    "path": "/maps/api/geocode/",
    "query": {
      "address": "1600 Amphitheatre Parkway, Mountain View, 9090",
      "region":"es",
      "language":"es"
    }
  }
 }
```
provided the `endpoint` configuration value is set to `maps.googleapis.com`, it will make a `GET` request to the endpoint on
```console
https://maps.googleapis.com/maps/api/geocode/region=es&language=es&address=1600+Amphitheatre+Parkway,+Mountain+View,+CA
```
The `endpoint` and `port` of the requests must be defined in the `application.conf` configuration file, whilst the `path`
can be defined both in the configuration or in the `request` object (the second takes precedence). The configuration
file also supports additional secret query parameters. So with the following configuration file we can make a
request to the [Google Geocoding service](https://developers.google.com/maps/documentation/geocoding/intro).
```hocon
# application.conf
flow {

  endpoint = "maps.googleapis.com"

  # additional parameters to be inculded in the http query if any
  extra_params {
    key = ${?API_KEY}
  }
}
```

The results is a JSON line which gets printed to the `stdout` with both the `request` and the `response` contents.
```json
{
  "request": {
    "method": "GET",
    "path": "/maps/api/geocode/",
    "query": {
      "address": "1600 Amphitheatre Parkway, Mountain View, 9090",
      "region":"es",
      "language":"es"
    }
  },
  "response": {
    "results": [
      {
        "address_commpontents": ...
      }
    ]
  }
}
```

In general, to represent the HTTP request as a JSON object some rules are contemplated ([#17](https://github.com/digitalorigin/batch-http/issues/17))
* The request must be wrapped in a `request` object in the JSON root.
* Inside the `request`, a `method` field can be used to indicate the [HTTP method](https://developer.mozilla.org/en-US/docs/Web/HTTP/Methods) for the request. If no method is provided and no default method is configured, `GET` will be used.
* The following attributes can also be specified to further refine the request.
  * A `path` string for passing the path to be used in the request. If no path is provided in the request or in the configuration, `/` will be used.
  * A `query` object for passing a set of key-value query parameters to be used in the request.
  * A `headers` object for passing a set of key-value headers to be used in the request.
  * A `body` object for sending a generic JSON object in the request body.
* A response is represented in a `response` object in the root. A `response` can contain `headers` and `body` as well. The response status is represented in a `status` field.
* Optionally a `context` object can also be passed in the root to allow for context propagation. This allows annotating input records with metadata which will not be used in the request ([#3](https://github.com/dcereijodo/batch-http/issues/3))

Any object or key not specified above will be simply ignored.


## Configuration
You can find examples and descriptions for all configurations supported by `batch-http` in the [sample configuration file](src/main/resources/application.conf). All this properties can be overridden on invocation by providing appropriate [JVM arguments](https://github.com/lightbend/config).

The util uses `logback` for logging. The default logging configuration can be found in the [`logback.xml` file](src/main/resources/logback.xml).

## Build and Run
This is an [SBT](https://www.scala-sbt.org/) project. If you don't have sbt installed, do so by running `brew install sbt`
on Mac. Then you can compile and package the project with
```bash
sbt ";compile;universal:packageBin"
```
And run it with
```bash
sbt run
```

The `packageBin` target from the [sbt-native-packager](https://www.scala-sbt.org/sbt-native-packager/) is creating a ZIP file
that contains all dependencies and executables. For running from the distribution package:

```console
$ unzip target/universal/batch-http-<version>.zip
$ batch-http-<version>/bin/batch-http -h
$ echo '{"request": {"method": "POST", "body": {"userId": 1, "title": "foo", "body": "bar"}, "path": "/posts", "context": "CvKL8"}}' | batch-http-<version>/bin/batch-http -Dflow.endpoint=jsonplaceholder.typicode.com 2&> /dev/null
```

## Integration Tests
Integration tests are based on [JSONPlaceholder](https://jsonplaceholder.typicode.com/) public REST service. They are skipped by
default during build. If you want to run them, you can do so with `sbt it:test`.

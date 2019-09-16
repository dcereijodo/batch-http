# BatchHttp [![CircleCI](https://circleci.com/gh/digitalorigin/batch-http.svg?style=svg&circle-token=d196d5b828e9e0debb5c25f04e7279c1f342d675)](https://circleci.com/gh/digitalorigin/batch-http)
A tool for processing data batches through a REST API. It reads the `stdin` for JSON lines, converts each line to an HTTP call and
then it makes the call. Finally it prints both the input line and the response to the `stdout`.

For example, when passed a JSON string (compacted in a single line)

```json
 {
   "query": {
     "address": "1600 Amphitheatre Parkway, Mountain View, 9090",
     "region":"es",
     "language":"es"
   }
 }
```

it will make a request with a query parameters string
```console
region=es&language=es&address=1600+Amphitheatre+Parkway,+Mountain+View,+CA
```
The `endpoint` and `path` of the request can defined in the `application.conf` configuration file or they can be
overridden by the `endpoint` and `path` keys in the input JSON payload (takes precedence). The configuration
file also supports additional secret query parameters. So with the following configuration file we can make a
request to the [Google Geocoding service](https://developers.google.com/maps/documentation/geocoding/intro).
```hocon
# application.conf
flow {

  endpoint = "maps.googleapis.com"
  path = "/maps/api/geocode/json"

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

- If a `query` object is passed in the JSON, a request will be created with all parameters inside
the object as URL parameters. `GET` method will be used.

- If a `body` object is passed in the JSON, a request will be created with the contents of `body`
in the request body. `POST` method will be used.

Whatever is passed in the input JSON in the `context` key it will be propagated unaltered to the result.
This allows annotating input records with metadata which will not be used in the request ([#3](https://github.com/dcereijodo/batch-http/issues/3))

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
$ echo '{"body": {"userId": 1, "title": "foo", "body": "bar"}, "path": "/posts", "context": "CvKL8"}' | batch-http-<version>/bin/batch-http -Dflow.endpoint=jsonplaceholder.typicode.com 2&> /dev/null
```

## Integration Tests
Integration tests are based on [JSONPlaceholder](https://jsonplaceholder.typicode.com/) public REST service. They are skipped by
default during build. If you want to run them, you can do so with `sbt it:test`.

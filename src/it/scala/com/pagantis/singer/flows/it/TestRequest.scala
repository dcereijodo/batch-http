package com.pagantis.singer.flows.it

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.HostConnectionPool
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.{Sink, Source}
import com.pagantis.singer.flows.Request
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{FlatSpec, Inside, Matchers}
import spray.json.DefaultJsonProtocol

import scala.concurrent.ExecutionContextExecutor
import scala.util.Try
import spray.json._

class TestRequest extends FlatSpec with Matchers with DefaultJsonProtocol with ScalaFutures with Inside {

  // init actor system, loggers and execution context
  implicit val system: ActorSystem = ActorSystem("BatchHttp")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val standardLogger: LoggingAdapter = Logging(system, getClass.getName)
  implicit val ec: ExecutionContextExecutor = system.dispatcher

  private implicit val connectionPool: Flow[(HttpRequest, Request), (Try[HttpResponse], Request), HostConnectionPool] =
    Http().cachedHostConnectionPoolHttps[Request]("jsonplaceholder.typicode.com")

  private implicit val defaultPatience: PatienceConfig =
    PatienceConfig(timeout = Span(15, Seconds), interval = Span(5, Millis))

  import Request._

  private def makeRequestAndHandle(line: String)(implicit connectionPool: Flow[(HttpRequest, Request),(Try[HttpResponse], Request), HostConnectionPool]) = {
    Source.single(line)
      .map(
        line => {
          val request = fromLine(line)
          (request.toAkkaRequest, request)
        }
      )
      .via(connectionPool)
      .mapAsync(8)(parseResponse(_))
      .runWith(Sink.head)

  }

  "Request" should "get comments by post_id" in {
    whenReady(makeRequestAndHandle("""{"request": {"method": "GET", "query": {"post_id": 1}, "path": "/comments"}}""")) {
      response => response.parseJson.asJsObject.fields("response").asJsObject.fields("body") shouldBe a[JsArray]
    }

    whenReady(makeRequestAndHandle("""{"request": {"method": "GET", "query": {"post_id": 1}, "path": "/comments"}, "context": "CvKL8"}""")) {
      responseRaw => {
        val responseAsJson = responseRaw.parseJson.asJsObject
        val fields = responseAsJson.fields

        fields("request") shouldBe JsObject(
          "method" -> JsString("GET"),
          "query" -> JsObject(
            "post_id" -> JsNumber(1)
          ),
          "path" -> JsString("/comments")
        )

        val responseFields = fields("response").asJsObject.fields
        responseFields("body") shouldBe a[JsArray]
        fields("context") shouldBe JsString("CvKL8")

        inside (responseFields("responded_at")) {
          case JsString(extractedAt) =>
            LocalDateTime.parse(extractedAt, DateTimeFormatter.ISO_DATE_TIME) shouldBe a[LocalDateTime]
          case _ => fail
        }
      }
    }
  }

  "Request" should "post posts with user_id" in {
    whenReady(makeRequestAndHandle("""{"request": {"method": "POST", "body": {"userId": 1, "title": "foo", "body": "bar"}, "path": "/posts"}}""")) {
      responseRaw => {
        val responseAsJson = responseRaw.parseJson.asJsObject
        val fields = responseAsJson.fields

        fields("request") shouldBe JsObject(
          "method" -> JsString("POST"),
          "body" -> JsObject(
            "title" -> JsString("foo"),
            "body" -> JsString("bar"),
            "userId" -> JsNumber(1)
          ),
          "path" -> JsString("/posts")
        )

        val response = fields("response")
        response.asJsObject.fields("body") shouldBe JsObject(
          "id" -> JsNumber(101),
          "title" -> JsString("foo"),
          "body" -> JsString("bar"),
          "userId" -> JsNumber(1)
        )

        inside (response.asJsObject.fields("responded_at")) {
          case JsString(extractedAt) =>
            LocalDateTime.parse(extractedAt, DateTimeFormatter.ISO_DATE_TIME) shouldBe a[LocalDateTime]
          case _ => fail
        }
      }
    }
  }
}


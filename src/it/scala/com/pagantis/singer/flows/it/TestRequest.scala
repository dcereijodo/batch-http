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
import com.pagantis.singer.flows.BatchHttp.clazz
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
  implicit val standardLogger: LoggingAdapter = Logging(system, clazz)
  implicit val ec: ExecutionContextExecutor = system.dispatcher

  implicit val connectionPool = Http().cachedHostConnectionPoolHttps[Request]("jsonplaceholder.typicode.com")

  implicit val defaultPatience =
    PatienceConfig(timeout = Span(2, Seconds), interval = Span(5, Millis))

  import Request._

  def makeRequestAndHandle(line: String)(implicit connectionPool: Flow[(HttpRequest, Request),(Try[HttpResponse], Request), HostConnectionPool]) = {

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

    whenReady(makeRequestAndHandle("""{"query": {"post_id": 1}, "path": "/comments"}""")) {
      response => response.parseJson.asJsObject.fields("response") shouldBe a[JsArray]
    }

    whenReady(makeRequestAndHandle("""{"query": {"post_id": 1}, "path": "/comments", "context": "CvKL8"}""")) {
      response => {
        val responseAsJson = response.parseJson.asJsObject
        val fields = responseAsJson.fields
        fields("request") shouldBe JsObject(
          "query" -> JsObject(
            "post_id" -> JsNumber(1)
          )
        )
        fields("response") shouldBe a[JsArray]
        fields("context") shouldBe JsString("CvKL8")
        inside (fields("extracted_at")) {
          case JsString(extractedAt) =>
            LocalDateTime.parse(extractedAt, DateTimeFormatter.ISO_DATE_TIME) shouldBe a[LocalDateTime]
          case _ => fail
        }
      }
    }

  }

  "Request" should "post posts with user_id" in {

    whenReady(makeRequestAndHandle("""{"body": {"userId": 1, "title": "foo", "body": "bar"}, "path": "/posts"}""")) {
      response => {
        val responseAsJson = response.parseJson.asJsObject
        val fields = responseAsJson.fields
        fields("request") shouldBe JsObject(
          "body" -> JsObject(
            "title" -> JsString("foo"),
            "body" -> JsString("bar"),
            "userId" -> JsNumber(1)
          )
        )
        fields("response") shouldBe JsObject(
          "id" -> JsNumber(101),
          "title" -> JsString("foo"),
          "body" -> JsString("bar"),
          "userId" -> JsNumber(1)
        )
        inside (fields("extracted_at")) {
          case JsString(extractedAt) =>
            LocalDateTime.parse(extractedAt, DateTimeFormatter.ISO_DATE_TIME) shouldBe a[LocalDateTime]
          case _ => fail
        }
      }
    }

  }

}


package com.pagantis.singer.flows.it

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
import org.scalatest.{FlatSpec, Matchers}
import spray.json.DefaultJsonProtocol

import scala.concurrent.ExecutionContextExecutor
import scala.util.Try
import spray.json._

class TestRequest extends FlatSpec with Matchers with DefaultJsonProtocol with ScalaFutures {

  // init actor system, loggers and execution context
  implicit val system: ActorSystem = ActorSystem("BatchHttp")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val standardLogger: LoggingAdapter = Logging(system, clazz)
  implicit val ec: ExecutionContextExecutor = system.dispatcher

  val connectionPool = Http().cachedHostConnectionPoolHttps[Request]("jsonplaceholder.typicode.com")

  implicit val defaultPatience =
    PatienceConfig(timeout = Span(2, Seconds), interval = Span(5, Millis))

  import Request._

  def makeRequestAndHandle(line: String, connectionPool: Flow[(HttpRequest, Request),(Try[HttpResponse], Request), HostConnectionPool]) = {

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

    whenReady(makeRequestAndHandle("""{"query": {"post_id": 1}, "path": "/comments"}""", connectionPool)) {
      response => response.parseJson.asJsObject.fields("response") shouldBe a[JsArray]
    }

    whenReady(makeRequestAndHandle("""{"query": {"post_id": 1}, "path": "/comments", "context": "CvKL8"}""", connectionPool)) {
      response => {
        val responseAsJson = response.parseJson.asJsObject
        val fields = responseAsJson.fields
        fields("response") shouldBe a[JsArray]
        fields("context") shouldBe JsString("CvKL8")
      }
    }

  }

}


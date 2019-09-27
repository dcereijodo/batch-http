package com.pagantis.singer.flows

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import akka.actor.{Actor, ActorSystem, Props}
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{JsonFraming, StreamConverters}
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor}
import scala.util.Success
import spray.json._

object BatchHttp extends App {

  val clazz = getClass.getName

  if(args.length > 0) {
    println("Reading from file not yet supported, try 'cat your-file > batch-http'")
    sys.exit(1)
  }
  val inputStream = System.in

  // init actor system, loggers and execution context
  implicit val system: ActorSystem = ActorSystem("BatchHttp")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val standardLogger: LoggingAdapter = Logging(system, clazz)
  implicit val ec: ExecutionContextExecutor = system.dispatcher

  val config = ConfigFactory.load()

  val endpoint = config.getString("flow.endpoint")
  val port = config.getInt("flow.port")
  val parallelism = config.getInt("flow.parallelism")
  val frameLength = config.getInt("flow.frame_length")

  // This shutdown sequence was copied from another related issue: https://github.com/akka/akka-http/issues/907#issuecomment-345288919
  def shutdownSequence =
    Http().shutdownAllConnectionPools

  // A sideways actor will maintain state
  class RunningSum extends Actor with akka.actor.ActorLogging {
    var total: Long = 0
    val startTime: LocalDateTime = LocalDateTime.now()

    private def logCount(count: Long): Unit = {
      val currentTime = LocalDateTime.now()
      val logRecord = JsObject(
        Map(
          "requests_processed" -> JsNumber(total),
          "timestamp" -> JsString(currentTime.format(DateTimeFormatter.ISO_DATE_TIME)),
        )
      )
      log.info(s"${logRecord.compactPrint}")
    }

    override def receive: Receive = {
      case increment: Int =>
        total = total + increment
        if(total % 5000 == 0) logCount(total)

    }

    override def postStop(): Unit = logCount(total)

  }
  val counter = system.actorOf(Props[RunningSum], "counter")


  import Request._

  val flowComputation =
    StreamConverters
      .fromInputStream(() => inputStream)
      .via(JsonFraming.objectScanner(frameLength))
      .log(clazz)
      .mapConcat(_.utf8String.split("\n").toList)
      .log(clazz)
      .map(
        line => {
          val request = fromLine(line)
          (request.toAkkaRequest, request)
        }
      )
      .log(clazz)
      .via(Http().cachedHostConnectionPoolHttps[Request](host = endpoint, port = port))
      .log(clazz)
      .mapAsync(parallelism)(parseResponse(_))
      .log(clazz)
      .map {
        line: String =>
          counter ! 1
          line
      }
      .runForeach(println(_))

  Await.ready(flowComputation, Duration.Inf)
  Await.ready(shutdownSequence, Duration.Inf)
  Await.ready(system.terminate, Duration.Inf)


  flowComputation.value match {
    case Some(Success(_)) => sys.exit(0)
    case _ => sys.exit(1)
  }

}

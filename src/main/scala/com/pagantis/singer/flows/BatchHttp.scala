package com.pagantis.singer.flows

import akka.actor.{ActorSystem, Props}
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.scaladsl.{Flow, JsonFraming, StreamConverters}
import com.typesafe.config.ConfigFactory
import net.ceedubs.ficus.Ficus._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor}
import scala.util.Success

object BatchHttp extends App {

  val clazz = getClass.getName

  if(args.length > 0) {
    println("Reading from file not yet supported, try 'cat your-file > batch-http'")
    sys.exit(1)
  }
  val inputStream = System.in

  val config = ConfigFactory.load()

  val endpoint = config.getString("flow.endpoint")
  val port = config.as[Option[Int]]("flow.port")
  val parallelism = config.getInt("flow.parallelism")
  val frameLength = config.getInt("flow.frame_length")
  val throttlingConfig = config.as[Option[Int]]("flow.throttle")

  /**
   * Instantiates a host connection pool ([[Flow[(HttpRequest, T), (Try[HttpResponse], T), HostConnectionPool]]])
   * and prepends a throttling stage if a throttling configuration is provided.
   * @return
   */
  def requestProcessor(implicit fm: Materializer) = {
    val throttleOrNeutral = throttlingConfig match {
      case Some(elements) => Flow[(HttpRequest, Request)].throttle(elements, 1 second)
      case _ => Flow[(HttpRequest, Request)]
    }
    val connectionPool = port match {
      case Some(number) => Http().cachedHostConnectionPoolHttps[Request](host = endpoint, port = number)
      case None => Http().cachedHostConnectionPoolHttps[Request](host = endpoint)
    }
    throttleOrNeutral.via(connectionPool)
  }

  // This shutdown sequence was copied from another related issue: https://github.com/akka/akka-http/issues/907#issuecomment-345288919
  def shutdownSequence = {
    for {
      _ <- Http().shutdownAllConnectionPools()
      akka <- system.terminate()
    } yield akka
  }

  // init actor system, loggers and execution context
  implicit val system: ActorSystem = ActorSystem("BatchHttp")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val standardLogger: LoggingAdapter = Logging(system, clazz)
  implicit val ec: ExecutionContextExecutor = system.dispatcher

  val counter = system.actorOf(Props[CountLogger], "counter")

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
      .via(requestProcessor)
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

  flowComputation.value match {
    case Some(Success(_)) => sys.exit(0)
    case _ => sys.exit(1)
  }

}

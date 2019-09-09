package com.pagantis.singer.flows

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.StreamConverters
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContextExecutor}
import scala.util.Success

object BatchHttp extends App {

  val clazz = getClass.getName

  // init actor system, loggers and execution context
  implicit val system: ActorSystem = ActorSystem("BatchHttp")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val standardLogger: LoggingAdapter = Logging(system, clazz)
  implicit val ec: ExecutionContextExecutor = system.dispatcher

  val config = ConfigFactory.load()

  val endpoint = config.getString("flow.endpoint")
  val parallelism = config.getInt("flow.parallelism")

  val startTime = System.nanoTime()

  // This shutdown sequence was copied from another related issue: https://github.com/akka/akka-http/issues/907#issuecomment-345288919
  def shutdownSequence =
    Http().shutdownAllConnectionPools


  import Request._

  val flowComputation =
    StreamConverters
      .fromInputStream(() => System.in)
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
      .via(Http().cachedHostConnectionPoolHttps[Request](host = endpoint))
      .log(clazz)
      .mapAsync(parallelism)(parseResponse(_))
      .log(clazz)
      .runForeach(println(_))

  Await.ready(flowComputation, Duration.Inf)

  standardLogger.info(s"Total execution time: ${(System.nanoTime - startTime)/1000000000} seconds")

  Await.ready(shutdownSequence, Duration.Inf)

  flowComputation.value match {
    case Some(Success(_)) => sys.exit(0)
    case _ => sys.exit(1)
  }

}

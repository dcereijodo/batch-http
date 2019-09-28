package com.pagantis.singer.flows

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import akka.actor.Actor
import spray.json.{JsNumber, JsObject, JsString}

/**
  * This is a helper actor that is instantiated on start up and receives messages
  * from the main [[BatchHttp]] stream on every record processed. It maintains
  * an internal counter of the current execution state and logs this information
  * accordingly
  */
class CountLogger extends Actor with akka.actor.ActorLogging {
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

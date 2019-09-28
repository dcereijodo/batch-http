package com.pagantis.singer.flows

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import akka.actor.Actor
import spray.json.{JsNumber, JsObject, JsString}

// A sideways actor will maintain state
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

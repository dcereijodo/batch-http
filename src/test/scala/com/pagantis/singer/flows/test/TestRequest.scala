package com.pagantis.singer.flows.test

import com.pagantis.singer.flows.{GET, POST, Request}
import org.scalatest.{FlatSpec, Inside, Matchers}
import spray.json.{DefaultJsonProtocol, JsNumber, JsObject, JsString, JsValue}

class TestRequest extends FlatSpec with Matchers with DefaultJsonProtocol with Inside{

  val get: JsObject = JsObject(
    "get" -> JsObject(
      "query" -> JsObject(
        "parm1" -> JsString("value1"),
        "parm2" -> JsString("value2")
      )
    )
  )

  val post: JsObject = JsObject(
    "post" -> JsObject(
      "body" -> JsObject(
        "parm1" -> JsString("value1"),
        "parm2" -> JsString("value2")
      )
    )
  )

  private def wrapRequest(methodObject: JsObject, optContext: Option[JsValue] = None) = optContext match {
    case Some(context) => JsObject("request" -> methodObject, "context" -> context)
    case None => JsObject("request" -> methodObject)
  }

  "Request" should "create GET request when a get method object is passed" in {
    inside(Request.fromLine(wrapRequest(get).compactPrint)) {
      case Request(method, _, _) => method shouldBe GET
    }
    inside(Request.fromLine(wrapRequest(get, Some(JsString("some_id"))).compactPrint)) {
      case Request(method, _, _) => method shouldBe GET
    }
  }

  "Request" should "create POST request when a body is passed" in {
    inside(Request.fromLine(wrapRequest(post).compactPrint)) {
      case Request(method, _, _) => method shouldBe POST
    }
    val request =
      Request
        .fromLine(wrapRequest(
            post,
            Some(JsObject("context" -> JsObject(Map("type" -> JsString("order"), "id" -> JsNumber(746)))))
          ).compactPrint)
    inside(request) {
      case Request(method, _, _) => method shouldBe POST
    }
  }
}

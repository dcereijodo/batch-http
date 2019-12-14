package com.pagantis.singer.flows.test

import akka.http.scaladsl.model.HttpMethods
import com.pagantis.singer.flows.Request
import org.scalatest.{FlatSpec, Inside, Matchers}
import spray.json.{DefaultJsonProtocol, JsNumber, JsObject, JsString, JsValue}

class TestRequest extends FlatSpec with Matchers with DefaultJsonProtocol with Inside{

  val get: JsObject = JsObject(
    "method" -> JsString("GET"),
    "query" -> JsObject(
      "parm1" -> JsString("value1"),
      "parm2" -> JsString("value2")
    )
  )

  val post: JsObject = JsObject(
    "method" -> JsString("POST"),
    "body" -> JsObject(
      "parm1" -> JsString("value1"),
      "parm2" -> JsString("value2")
    )
  )

  private def wrapRequest(methodObject: JsObject, optContext: Option[JsValue] = None) = optContext match {
    case Some(context) => JsObject("request" -> methodObject, "context" -> context)
    case None => JsObject("request" -> methodObject)
  }

  "Request" should "create GET request when a get method object is passed" in {
    inside(Request.fromLine(wrapRequest(get).compactPrint)) {
      case Request(method, _, _) => method shouldBe HttpMethods.GET
    }
    val context = JsString("some_id")
    inside(Request.fromLine(wrapRequest(get, Some(context)).compactPrint)) {
      case Request(method, _, Some(requestContext)) =>
        method shouldBe HttpMethods.GET
        requestContext shouldBe context
      case _ => fail
    }
  }

  "Request" should "create POST request when a body is passed" in {
    inside(Request.fromLine(wrapRequest(post).compactPrint)) {
      case Request(method, _, _) => method shouldBe HttpMethods.POST
    }
    val context = JsObject("context" -> JsObject(Map("type" -> JsString("order"), "id" -> JsNumber(746))))
    val request =
      Request
        .fromLine(wrapRequest(
            post,
            Some(context)
          ).compactPrint)
    inside(request) {
      case Request(method, _, Some(requestContext)) =>
        method shouldBe HttpMethods.POST
        requestContext shouldBe context
      case _ => fail
    }
  }
}

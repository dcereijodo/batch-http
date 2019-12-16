package com.pagantis.singer.flows.test

import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, Uri}
import com.pagantis.singer.flows.{InvalidRequestException, Request}
import org.scalatest.{FlatSpec, Inside, Matchers}
import spray.json.{DefaultJsonProtocol, JsNumber, JsObject, JsString, JsValue}

class TestRequest extends FlatSpec with Matchers with DefaultJsonProtocol with Inside{

  val get: JsObject = JsObject(
    "method" -> JsString("GET"),
    "query" -> JsObject(
      "parm1" -> JsString("value1"),
      "parm2" -> JsString("value2")
    ),
    "headers" -> JsObject(
      "Some header key" -> JsString("Some header value")
    )
  )

  val post: JsObject = JsObject(
    "method" -> JsString("POST"),
    "body" -> JsObject(
      "parm1" -> JsString("value1"),
      "parm2" -> JsString("value2")
    ),
    "query" -> JsObject(
      "var" -> JsString("var_value_1")
    ),
    "path" -> JsString("/some_path")
  )

  private def wrapRequest(methodObject: JsObject, optContext: Option[JsValue] = None) = optContext match {
    case Some(context) => JsObject("request" -> methodObject, "context" -> context)
    case None => JsObject("request" -> methodObject)
  }

  "Request" should "create GET request" in {
    val context = JsString("some_id")
    val request = Request.fromLine(wrapRequest(get, Some(context)).compactPrint)

    inside(request.toAkkaRequest) {
      case HttpRequest(HttpMethods.GET, _, headers, _, _) =>
        headers should contain (RawHeader("Some header key", "Some header value"))
      case _ => fail
    }

    inside(request) {
      case Request(method, _, Some(requestContext)) =>
        method shouldBe HttpMethods.GET
        requestContext shouldBe context
      case _ => fail
    }
  }

  "Request" should "create POST request" in {
    val context = JsObject("context" -> JsObject(Map("type" -> JsString("order"), "id" -> JsNumber(746))))
    val request = Request.fromLine(wrapRequest(post, Some(context)).compactPrint)

    inside(request.toAkkaRequest) {
      case HttpRequest(HttpMethods.POST, Uri(_, _, path, rawQueryString, _), _, _, _) =>
        rawQueryString shouldBe Some("var=var_value_1")
        path.toString shouldBe "/some_path"
      case _ => fail
    }

    inside(request) {
      case Request(_, _, Some(requestContext)) =>
        requestContext shouldBe context
      case _ => fail
    }
  }

  "Request" should "fail invalid representations" in {
    assertThrows[InvalidRequestException] { Request.fromLine("""{}""") }

    (intercept[InvalidRequestException] {
      Request.fromLine("""{"request": {"method": "INVALID_METHOD"}}""")
    } getMessage) shouldBe "'method' must be a valid HTTP method"

    (intercept[InvalidRequestException] {
      Request.fromLine("""{"request": {"method": 200}}""")
    } getMessage) shouldBe "'method' must be a JSON string"

    (intercept[InvalidRequestException] {
      Request.fromLine("""{"request": {"method": "GET", "query": {"param": {}}}}""").toAkkaRequest
    } getMessage) shouldBe "invalid query parameter {}: it must be a string or a number"

    (intercept[InvalidRequestException] {
      Request.fromLine("""{"request": {"method": "GET", "query": "query_val"}}""").toAkkaRequest
    } getMessage) shouldBe "'query' member must be key-value map"

    (intercept[InvalidRequestException] {
      Request.fromLine("""{"request": {"method": "GET", "headers": {"header_1": {}}}}""").toAkkaRequest
    } getMessage) shouldBe "invalid header (header_1,{}): it must be a string or a number"

    (intercept[InvalidRequestException] {
      Request.fromLine("""{"request": {"method": "GET", "headers": "header_val"}}""").toAkkaRequest
    } getMessage) shouldBe "'headers' member must be key-value map"

    (intercept[InvalidRequestException] {
      Request.fromLine("""{"request": {"method": "POST", "body": "body_val"}}""").toAkkaRequest
    } getMessage) shouldBe "'body' must be a full JSON object"

    (intercept[InvalidRequestException] {
      Request.fromLine("""{"request": {"method": "POST", "body": {}, "path": {}}}""").toAkkaRequest
    } getMessage) shouldBe "'path' must be an JSON string"
  }
}

package com.pagantis.singer.flows.test

import com.pagantis.singer.flows.{GetRequest, PostRequest, Request}
import org.scalatest.{FlatSpec, Matchers}
import spray.json.{DefaultJsonProtocol, JsNumber, JsObject, JsString}

class TestRequest extends FlatSpec with Matchers with DefaultJsonProtocol {

  val query = JsObject(
    "query" -> JsObject(
      "parm1" -> JsString("value1"),
      "parm2" -> JsString("value2")
    )
  )

  val body = JsObject(
    "body" -> JsObject(
      "parm1" -> JsString("value1"),
      "parm2" -> JsString("value2")
    )
  )

  "Request" should "create GET request when a query map is passed" in {

    Request.fromLine(query.compactPrint) shouldBe a[GetRequest]
    Request.fromLine(
      JsObject(
        query.fields + ("context" -> JsString("some_id"))
      ) compactPrint
    ) shouldBe a[GetRequest]

  }

  "Request" should "create POST request when a body is passed" in {

    Request.fromLine(body.compactPrint) shouldBe a[PostRequest]
    Request.fromLine(
      JsObject(
        body.fields + ("context" -> JsObject(Map("type" -> JsString("order"), "id" -> JsNumber(746))))
      ) compactPrint
    ) shouldBe a[PostRequest]

  }

}

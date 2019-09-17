package com.pagantis.singer.flows

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, Uri}
import akka.http.scaladsl.model.Uri.Query
import akka.stream.Materializer
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import net.ceedubs.ficus.Ficus._
import spray.json._

import scala.util.{Failure, Success, Try}

object Request {

  val config = ConfigFactory.load()

  val extraParams = config.as[Map[String, String]]("flow.extra_params")
  val defaultPath = config.getString("flow.path")

  def fromLine(line: String) = {

    val message = line.parseJson
    val fields = message.asJsObject.fields

    (fields.get("query"), fields.get("body"), fields.get("path"), fields.get("context")) match {

      case (Some(_), Some(_), _, _) =>
        throw new Exception

      case (Some(jsonQueryParams), None, optPath, optContext) =>

        val asStringMap = jsonQueryParams
          .asJsObject
          .fields

        val optStringPath = optPath collect { case JsString(value) => value }

        GetRequest(asStringMap ++ extraParams.map(pair => (pair._1, JsString(pair._2))), optStringPath, optContext)

      case (None, Some(jsonBody), optPath, optContext) =>

        val optStringPath = optPath collect { case JsString(value) => value }

        PostRequest(jsonBody.asJsObject, optStringPath, optContext)
    }

  }

  def parseResponse(triedResponse: (Try[HttpResponse], Request))(implicit am: Materializer) = {

    implicit val ec = am.executionContext

    triedResponse match {
      case (Success(response), request) =>
        Request.fromHttpResponse(response).map(request.toLine(_))
      case (Failure(exception), _) => throw exception
    }

  }



  def fromHttpResponse(response: HttpResponse)(implicit am: Materializer) = {

    import spray.json._
    implicit val ec = am.executionContext

    val responseAsJson = response.entity.dataBytes.runFold(ByteString(""))(_ ++ _) map
      (body => body.utf8String.parseJson)


    responseAsJson

  }

}


trait Request {

  val context: Option[JsValue]

  def toAkkaRequest: HttpRequest

  def outputRequest: JsObject

  def toLine(response: JsValue, extractedAt: LocalDateTime = LocalDateTime.now()): String = {

    val request = outputRequest

    val requestAndResponse =
      Map(
        "request" -> request,
        "response" -> response,
        "extracted_at" -> JsString(extractedAt.format(DateTimeFormatter.ISO_DATE_TIME))
      )

    val outputKeys = context match {
      case Some(ctx) => requestAndResponse + ("context" -> ctx)
      case None => requestAndResponse
    }

    JsObject(outputKeys).compactPrint
  }

}

case class GetRequest(queryParams: Map[String, JsValue], path: Option[String] = None, context: Option[JsValue]) extends Request {

  override def toAkkaRequest: HttpRequest = {

    val query = Query(
      queryParams
        .collect {
          case (key, JsString(value)) => (key, value)
          case (key, JsNumber(value)) => (key, value.toString)
      }
    )

    HttpRequest(
      method = HttpMethods.GET,
      uri = path match {
        case None => Uri(Request.defaultPath).withQuery(query)
        case Some(path) => Uri(path).withQuery(query)
      }
    )
  }

  override def outputRequest: JsObject = {

    val noSecrets = queryParams.filter(param => !Request.extraParams.contains(param._1))

    JsObject("query" -> JsObject(noSecrets))
  }

}

case class PostRequest(body: JsObject, path: Option[String] = None, context: Option[JsValue]) extends Request {

  override def toAkkaRequest: HttpRequest = HttpRequest(
    method = HttpMethods.POST,
    uri = path match {
      case None => Uri(Request.defaultPath)
      case Some(path) => Uri(path)
    },
    entity = HttpEntity(ContentTypes.`application/json`, body.compactPrint)
  )

  override def outputRequest: JsObject = JsObject("body" -> body)

}

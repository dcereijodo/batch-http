package com.pagantis.singer.flows

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpHeader, HttpMethods, HttpRequest, HttpResponse, Uri}
import akka.http.scaladsl.model.Uri.{Path, Query}
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.Materializer
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import net.ceedubs.ficus.Ficus._
import spray.json._

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success, Try}

object Request {

  private val config = ConfigFactory.load()

  private val extraParams: Map[String, String] =
    config.as[Option[Map[String, String]]]("flow.extra_params") match {
      case None => Map()
      case Some(params) => params
    }

  private val defaultPath = config.getString("flow.path")

  def fromLine(line: String): Request = {

    val message = line.parseJson
    val rootFields = message.asJsObject.fields
    val request = rootFields.get("request")
    val optContext = rootFields.get("context")

    request match {
      case Some(JsObject(requestFields)) =>
        (requestFields.get("get"), requestFields.get("post")) match {
          case (Some(_), Some(_)) => throw InvalidRequestException("'get' and 'post' methods are mutually exclusive")
          case (Some(JsObject(methodContents)), None) => Request(GET, methodContents, optContext)
          case (None, Some(JsObject(methodContents))) => Request(POST, methodContents, optContext)
          case _ => throw new InvalidRequestException
        }
      case _ => throw new InvalidRequestException
    }


  }

  def parseResponse(triedResponse: (Try[HttpResponse], Request))(implicit am: Materializer): Future[String] = {
    implicit val ec: ExecutionContextExecutor = am.executionContext

    triedResponse match {
      case (Success(response), request) =>
        Request.fromHttpResponse(response).map(request.toLine(_))
      case (Failure(exception), _) => throw exception
    }
  }

  def fromHttpResponse(response: HttpResponse)(implicit am: Materializer): Future[JsValue] = {
    import spray.json._
    implicit val ec: ExecutionContextExecutor = am.executionContext

    val responseAsJson = response.entity.dataBytes.runFold(ByteString(""))(_ ++ _) map
      (body => body.utf8String.parseJson)

    responseAsJson
  }
}

sealed trait RequestMethod
case object GET extends RequestMethod
case object POST extends RequestMethod

case class Request(method: RequestMethod, methodContents: Map[String, JsValue], context: Option[JsValue]) {

  def outputRequest: JsObject = {
    method match {
      case GET => JsObject("get" -> JsObject(methodContents))
      case POST => JsObject("post" -> JsObject(methodContents))
    }
  }

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

  private def buildQueryString(query: Option[JsValue]) = {
    query match {
      case Some(JsObject(fields)) => Some(fields.mapValues(_.toString) ++ Request.extraParams)
      case None if Request.extraParams.nonEmpty => Some(Request.extraParams)
      case None => None
      case _ => throw InvalidRequestException("'query' member must be key-value map")
    }
  }

  def buildHeaders(headers: Option[JsValue]): collection.immutable.Seq[HttpHeader] = {
    headers match {
      case Some(JsObject(fields)) => fields map { case (header, value) => RawHeader(header, value.toString) } toList
      case None => List()
      case _ => throw InvalidRequestException("'headers' member must be key-value map")
    }
  }

  def baseRequest: HttpRequest = {
    val queryString = buildQueryString(methodContents.get("query"))
    val headers = buildHeaders(methodContents.get("header"))

    HttpRequest(
      uri = methodContents.get("path") match {
        case Some(JsString(path)) => Uri(path = Path(path), queryString = queryString.map(Query(_).toString))
        case _ => Uri(path = Path(Request.defaultPath), queryString = queryString.map(Query(_).toString))
      },
      headers = headers
    )
  }

  def toAkkaRequest: HttpRequest = {
    method match {
      case GET => baseRequest.withMethod(HttpMethods.GET)
      case POST => baseRequest.withMethod(HttpMethods.POST).withEntity(
        HttpEntity(ContentTypes.`application/json`, methodContents.get("body").map(_.compactPrint).getOrElse(""))
      )
    }
  }
}
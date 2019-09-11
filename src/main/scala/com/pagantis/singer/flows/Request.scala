package com.pagantis.singer.flows

import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse, Uri}
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
          .collect {
            case (key, JsString(value)) => (key, value)
            case (key, JsNumber(value)) => (key, value.toString)
          }

        val optStringPath = optPath collect { case JsString(value) => value }

        GetRequest(asStringMap ++ extraParams, optStringPath, optContext)

      case (None, Some(jsonBody), optPath, optContext) =>

        val optStringPath = optPath collect { case JsString(value) => value }

        PostRequest(jsonBody.asJsObject, optStringPath, optContext)
    }

  }

  def parseResponse(triedResponse: (Try[HttpResponse], Request))(implicit am: Materializer) = {

    implicit val ec = am.executionContext

    triedResponse match {
      case (Success(response), request) =>
        Request.fromHttpResponse(response).map(request.toLine)
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

  def toAkkaRequest: HttpRequest

  def toLine(response: JsValue): String

}

case class GetRequest(queryParams: Map[String, String], path: Option[String] = None, context: Option[JsValue]) extends Request {

  override def toAkkaRequest: HttpRequest = {
    val query = Query(queryParams)
    HttpRequest(
      method = HttpMethods.GET,
      uri = path match {
        case None => Uri(Request.defaultPath).withQuery(query)
        case Some(path) => Uri(path).withQuery(query)
      }
    )
  }

  override def toLine(response: JsValue): String = {

    val noSecrets = queryParams.filter(param => !Request.extraParams.contains(param._1))

    val requestAndResponse =
      Map(
        "request" -> JsObject(noSecrets.map{ case (key, value) => (key, JsString(value))}),
        "response" -> response
      )

    val outputKeys = context match {
      case Some(ctx) => requestAndResponse + ("context" -> ctx)
      case None => requestAndResponse
    }

    JsObject(outputKeys).compactPrint

  }

}

case class PostRequest(body: JsObject, path: Option[String] = None, context: Option[JsValue]) extends Request {

  override def toAkkaRequest: HttpRequest = ???

  override def toLine(response: JsValue): String = ???

}

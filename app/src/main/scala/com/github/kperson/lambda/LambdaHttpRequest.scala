package com.github.kperson.lambda

import java.io.{ByteArrayInputStream, InputStream}

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.HttpHeader.ParsingResult.Ok

sealed trait Method {

  def akkaHttpMethod: akka.http.scaladsl.model.HttpMethod = {
    this match {
      case GET => HttpMethods.GET
      case POST => HttpMethods.POST
      case PUT => HttpMethods.PUT
      case DELETE => HttpMethods.DELETE
      case PATCH => HttpMethods.PATCH
      case OPTIONS => HttpMethods.OPTIONS
      case HEAD => HttpMethods.HEAD
      case CONNECT => HttpMethods.CONNECT
      case TRACE => HttpMethods.TRACE
    }
  }

}

case object GET extends Method
case object POST extends Method
case object PUT extends Method
case object DELETE extends Method
case object PATCH extends Method
case object OPTIONS extends Method
case object TRACE extends Method
case object CONNECT extends Method
case object HEAD extends Method



case class LambdaHttpRequest(
  httpMethod: Method,
  path: String,
  body: Option[String] = None,
  queryStringParameters: Option[Map[String, String]] = None,
  headers: Map[String, String] = Map.empty,
  isBase64Encoded: Boolean
) {

  def normalize(): LambdaHttpRequest = {
    body match {
      case Some(a) if isBase64Encoded =>
        val bs = java.util.Base64.getDecoder.decode(a)
        copy(body = Some(new String(bs)), isBase64Encoded = false)
      case _ => this
    }
  }

  def bodyBytes: Array[Byte] = body match {
    case Some(a) if isBase64Encoded => java.util.Base64.getDecoder.decode(a)
    case Some(a) => a.getBytes
    case _ => Array.emptyByteArray
  }

  def bodyInputStream: InputStream = body match {
    case Some(a) if isBase64Encoded => new ByteArrayInputStream(java.util.Base64.getDecoder.decode(a))
    case Some(a) => new ByteArrayInputStream(a.getBytes)
    case _ => new ByteArrayInputStream(Array.emptyByteArray)
  }

  def bodyOrEmpty: String = body.getOrElse("")

  def akkaHttpRequest = {
    val akkaHeaders = headers.map { case (k, v) =>
      HttpHeader.parse(k, v)
    }.collect {
      case Ok(header, _) => header
    }.toList

    val contentTypeStr = headers
      .find { case (k, _) if k.toLowerCase() == "content-type" => true }
      .map { case (_, v)  => v }
      .getOrElse("application/octet-stream")
    val contentType = ContentType.parse(contentTypeStr) match {
      case Right(c) => Some(c)
      case _ if body.isDefined => Some(ContentTypes.`application/octet-stream`)
      case _ => None
    }

    HttpRequest(
      httpMethod.akkaHttpMethod,
      Uri(if(path.startsWith("/")) path else "/" + path),
      akkaHeaders,
      contentType.map { HttpEntity(_, bodyBytes) }.getOrElse(HttpEntity.Empty),
      HttpProtocols.`HTTP/1.1`
    )
  }

}
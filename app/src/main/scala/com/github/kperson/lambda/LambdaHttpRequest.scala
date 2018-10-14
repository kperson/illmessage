package com.github.kperson.lambda

import java.io.{ByteArrayInputStream, InputStream}

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.HttpHeader.ParsingResult.Ok

import io.lemonlabs.uri.QueryString

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
  headers: Option[Map[String, String]] = None,
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

  def akkaHttpRequest: HttpRequest = {

    val akkaHeaders = headers.getOrElse(Map[String, String]()).map { case (k, v) =>
      HttpHeader.parse(k, v)
    }.collect {
      case Ok(header, _) => header
    }.toList

    val contentTypeStr = headers.getOrElse(Map[String, String]())
      .find { case (k, _) => k.toLowerCase() == "content-type" }
      .map { case (_, v)  => v }
      .getOrElse("application/octet-stream")

    val contentType = ContentType.parse(contentTypeStr) match {
      case Right(c) if bodyOrEmpty.nonEmpty => Some(c)
      case _ if bodyOrEmpty.nonEmpty => Some(ContentTypes.`application/octet-stream`)
      case _ => None
    }

    val paramStr = queryStringParameters.map { x => QueryString.fromTraversable(x.toList) }
    val basePath = if(path.startsWith("/")) path else "/" + path

    val uriStr = paramStr match {
      case Some(p) => basePath + p.toString
      case _ => basePath
    }

    HttpRequest(
      httpMethod.akkaHttpMethod,
      Uri(uriStr),
      akkaHeaders,
      contentType.map { HttpEntity(_, bodyBytes) }.getOrElse(HttpEntity.Empty),
      HttpProtocols.`HTTP/1.1`
    )
  }

}
package com.github.kperson.lambda

import java.io.{ByteArrayInputStream, InputStream}


sealed trait Method

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
        copy(body = Some(new String(bs)), isBase64Encoded = false, path =  if(path.startsWith("/")) path else "/" + path)
      case _ =>
        copy(path = if(path.startsWith("/")) path else "/" + path)
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

}
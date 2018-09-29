package com.github.kperson.aws.lambda

import java.io.{ByteArrayInputStream, InputStream, OutputStream}
import java.nio.charset.StandardCharsets

import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}
import org.json4s.NoTypeHints
import org.json4s.jackson.Serialization
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization.write

import scala.collection.Map
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Success, Try}


object JSON {

  implicit class ToJSON[A <: AnyRef](self: A) {

    def toJSONStr: String = {
      val formats = Serialization.formats(NoTypeHints)
      write(parse(write(self)(formats)))(formats)
    }

    def toJSONBytes: Array[Byte] = {
      toJSONStr.getBytes(StandardCharsets.UTF_8)
    }
  }

  implicit class FromJSONInputStream(self: InputStream) {

    def extract[A](camelize: Boolean)(implicit mf: scala.reflect.Manifest[A]): A = {
      val formats = Serialization.formats(NoTypeHints)
      if (camelize) {
        parse(self).camelizeKeys.extract[A](formats, mf)
      }
      else {
        parse(self).extract[A](formats, mf)
      }
    }
  }

  implicit class FromJSONStr(self: String) {

    def extract[A](camelize: Boolean)(implicit mf: scala.reflect.Manifest[A]): A = {
      val in = new ByteArrayInputStream(self.getBytes(StandardCharsets.UTF_8))
      in.extract[A](camelize)
    }

  }

  implicit class FromJSONByte(self: Array[Byte]) {

    def extract[A](camelize: Boolean)(implicit mf: scala.reflect.Manifest[A]): A = new ByteArrayInputStream(self).extract(camelize)

  }

}


case class HttpResponse(statusCode: Int = 200, body: String, headers: Map[String, String] = Map.empty)

object JSONHttpResponse {

  import JSON._

  def apply[A <: AnyRef](statusCode: Int = 200, body: A): HttpResponse = {
    HttpResponse(statusCode, body.toJSONStr, headers = Map("Content-Type" -> "application/json"))
  }

}


abstract class AsyncStreamLambdaHandler extends RequestStreamHandler {

  import JSON._

  def handleRequest(i: InputStream, o: OutputStream, context: Context) {
    val f = handle(i)
    f.failed.foreach { _ =>
      val payload = Map("error" -> "an unknown error has occurred")
      val res = HttpResponse(
        statusCode = 500,
        body = payload.toJSONStr,
        headers = Map("Content-Type" -> "application/json")
      )
      val rs = write(res)(Serialization.formats(NoTypeHints)).getBytes(StandardCharsets.UTF_8)
      o.write(rs)
      o.close()
    }

    f.foreach { rs =>
      o.write(rs)
      o.close()
    }
  }

  def handle(in: InputStream): Future[Array[Byte]]

}


abstract class HttpLambdaHandler[R](implicit mf: scala.reflect.Manifest[R]) extends AsyncStreamLambdaHandler {

  import JSON._

  def handleHttpRequest(httpRequest: R): Future[HttpResponse]

  def handle(in: InputStream): Future[Array[Byte]] = {
    val httpResponseFut = Try(in.extract[R](camelize = true)) match {
      case Success(req) =>
        handleHttpRequest(req)
      case _ =>
        val payload = Map("error" -> "invalid request")
        Future.successful(HttpResponse(
          statusCode = 400,
          body = payload.toJSONStr,
          headers = Map("Content-Type" -> "application/json")
        ))
    }

    httpResponseFut.map { httpResponse =>
      write(httpResponse)(Serialization.formats(NoTypeHints)).getBytes(StandardCharsets.UTF_8)
    }.recover { case _ =>
      val payload = Map("error" -> "an unknown error has occurred")
      val res = HttpResponse(
        statusCode = 500,
        body = payload.toJSONStr,
        headers = Map("Content-Type" -> "application/json")
      )
      write(res)(Serialization.formats(NoTypeHints)).getBytes(StandardCharsets.UTF_8)
    }
  }
}

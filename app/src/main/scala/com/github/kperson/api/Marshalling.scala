package com.github.kperson.api


import akka.http.scaladsl.marshalling.{Marshaller, ToEntityMarshaller, ToResponseMarshaller}
import akka.http.scaladsl.model.{HttpRequest, MediaTypes, StatusCode}
import akka.http.scaladsl.unmarshalling.FromRequestUnmarshaller
import akka.stream.Materializer
import java.nio.charset.StandardCharsets

import com.github.kperson.serialization.JSONFormats
import org.json4s.Formats
import org.json4s.jackson.Serialization._

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.Manifest


private class JSONUnmarshaller[T](implicit formats: Formats, mf: Manifest[T]) extends FromRequestUnmarshaller[T] {
  def apply(value: HttpRequest)(implicit ec: ExecutionContext, materializer: Materializer): Future[T] = {
    val buffer = ArrayBuffer[Byte]()

    val fetch = value.entity.dataBytes.runForeach { a => buffer.appendAll(a.toArray) }
    fetch.map { _ => buffer.toArray }(materializer.executionContext)
      .map { bytes =>
        val json = new String(bytes, StandardCharsets.UTF_8)
        read[T](json)
      }
  }
}

trait MarshallingSupport {

  implicit lazy val formats: Formats = JSONFormats.formats

  implicit def fromResponseMarshaller[T](implicit formats: Formats, mf: Manifest[T]): FromRequestUnmarshaller[T] = new JSONUnmarshaller[T]()

  def toAPIResponseEntityMarshaller[T <: AnyRef](implicit formats: Formats): ToEntityMarshaller[(StatusCode, T)] = {
    Marshaller.StringMarshaller.wrap(MediaTypes.`application/json`)(r => write[T](r._2))
  }

  implicit def toAPIResponseResponseMarshaller[T <: AnyRef](implicit formats: Formats): ToResponseMarshaller[(StatusCode, T)] =
    Marshaller
    .fromStatusCodeAndHeadersAndValue(toAPIResponseEntityMarshaller[T])
    .compose(apiResult => (apiResult._1, List.empty, apiResult))

}
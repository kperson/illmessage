package com.github.kperson.aws

import java.io.InputStream
import java.net.URI
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers

import scala.collection.JavaConverters._
import scala.concurrent.{Future, Promise}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._


case class AWSHttpResponse[T](status: Int, headers: Map[String, String] = Map.empty, body: T) {

  lazy val lowerHeaders: Map[String, String] = headers.map { case (k, v) => (k.toLowerCase, v) }

}


case class AWSError[T](response: AWSHttpResponse[T]) extends RuntimeException(response.status.toString)

object AWSHttp {

  implicit class NativeHttpResponseExtension[T](self: java.net.http.HttpResponse[T]) {

    def toAWSResponse: AWSHttpResponse[T] = {
      AWSHttpResponse(
        self.statusCode,
        self.headers().map().asScala.map { case (k, v) =>
          (k, v.get(0))
        }.toMap,
        self.body()
      )
    }

  }


  implicit class NativeHttpClientExtension(self: java.net.http.HttpClient) {

    def future(request: java.net.http.HttpRequest): Future[AWSHttpResponse[Array[Byte]]] = {
      val p = Promise[AWSHttpResponse[Array[Byte]]]()
      self.sendAsync(request, BodyHandlers.ofByteArray()).thenAccept((rs) => {
        if (rs.statusCode() < 400) {
          p.success(rs.toAWSResponse)
        }
        else {
          p.failure(AWSError(rs.toAWSResponse))
        }
      }).whenComplete { (_, error) =>
        if(error != null) {
          p.failure(error)
        }
      }
      p.future
    }

    def futureInputStream(request: java.net.http.HttpRequest): Future[AWSHttpResponse[InputStream]] = {
      val p = Promise[AWSHttpResponse[InputStream]]()
      self.sendAsync(request, BodyHandlers.ofInputStream()).thenAccept((rs) => {
        if (rs.statusCode() < 400) {
          p.success(rs.toAWSResponse)
        }
        else {
          p.failure(AWSError(rs.toAWSResponse))
        }
      }).whenComplete { (_, error) =>
        if(error != null) {
          p.failure(error)
        }
      }
      p.future
      p.future
    }

    def awsRequestFuture(url: String, signing: AWSSigning, timeout: FiniteDuration = 10.seconds): Future[AWSHttpResponse[Array[Byte]]] = {
      val finalURL = if(signing.encodedParams.nonEmpty) {
        val p = signing.encodedParams.toList.map { case (k, v) =>
          s"$k=$v"
        }.mkString("&")
        s"$url?$p"
      }
      else {
        url
      }
      val builder = java.net.http.HttpRequest.newBuilder(new URI(finalURL))

      signing.headers.foreach { case (k, v) =>
        if (k.toLowerCase != "host") {
          builder.setHeader(k, v)
        }
      }
      if (!signing.payload.isEmpty) {
        builder.method(signing.httpMethod, BodyPublishers.ofByteArray(signing.payload))
      }
      builder.timeout(java.time.Duration.ofMillis(timeout.toMillis))
      future(builder.build())
    }

  }

}


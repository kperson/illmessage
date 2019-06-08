package com.github.kperson.aws

import java.net.URI
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers

import scala.collection.JavaConverters._
import scala.concurrent.{Future, Promise}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._


case class AWSHttpResponse(status: Int, headers: Map[String, String] = Map.empty, body: Array[Byte] = Array.empty) {

  lazy val lowerHeaders: Map[String, String] = headers.map { case (k, v) => (k.toLowerCase, v) }

}

case class AWSError(response: AWSHttpResponse) extends RuntimeException(new String(response.body))

object AWSHttp {

  implicit class NativeHttpResponseExtension(self: java.net.http.HttpResponse[Array[Byte]]) {

    def toAWSResponse: AWSHttpResponse = {
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

    def future(request: java.net.http.HttpRequest): Future[AWSHttpResponse] = {
      val p = Promise[AWSHttpResponse]()
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

    def awsRequestFuture(url: String, signing: AWSSigning, timeout: FiniteDuration = 10.seconds): Future[AWSHttpResponse] = {
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


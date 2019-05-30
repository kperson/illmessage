package com.github.kperson.aws

import io.netty.handler.codec.http.HttpHeaders

import org.asynchttpclient._

import scala.collection.JavaConverters._
import scala.concurrent.{Future, Promise}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

case class AWSHttpResponse(status: Int, headers: Map[String, String] = Map.empty, body: Array[Byte] = Array.empty)

case class AWSError(response: AWSHttpResponse) extends RuntimeException(new String(response.body))

object AWSHttp {

  implicit class HttpClientExtension(self: AsyncHttpClient) {

    def future(builder: RequestBuilder, signing: AWSSigning, timeout: FiniteDuration = 10.seconds): Future[AWSHttpResponse] = {
      val promise = Promise[AWSHttpResponse]()
      signing.headers.foreach { case (k, v) =>
        if (k.toLowerCase != "host") {
          builder.setHeader(k, v)
        }
      }
      if (!signing.payload.isEmpty) {
        builder.setBody(signing.payload)
      }
      signing.encodedParams.map { case (k, v) =>
        builder.addQueryParam(k, v)
      }
      builder.setRequestTimeout(timeout.toMillis.toInt)
      requestFuture(builder.build())
    }

    def requestFuture(request: Request): Future[AWSHttpResponse] = {
      val promise = Promise[AWSHttpResponse]()
      self.executeRequest(request, new AsyncHandler[Int] {

        private var response = AWSHttpResponse(-1)

        def onStatusReceived(responseStatus: HttpResponseStatus): AsyncHandler.State =  {
          response = response.copy(status = responseStatus.getStatusCode)
          AsyncHandler.State.CONTINUE
        }

        def onHeadersReceived(onHeadersReceived: HttpHeaders): AsyncHandler.State =  {
          val headers: Map[String, String] = onHeadersReceived.entries()
            .asScala
            .map { x => (x.getKey, x.getValue)
            }.toMap
          response = response.copy(headers = headers)
          AsyncHandler.State.CONTINUE
        }

        def onBodyPartReceived(onHeadersReceived: HttpResponseBodyPart): AsyncHandler.State = {
          response = response.copy(body = response.body ++ onHeadersReceived.getBodyPartBytes)
          AsyncHandler.State.CONTINUE
        }

        def onCompleted(): Int = {
          if(response.status < 400) {
            promise.trySuccess(response)
          }
          else {
            promise.tryFailure(AWSError(response))
          }
          response.status
        }

        def onThrowable(t: Throwable) {
          promise.tryFailure(t)
        }

      })
      promise.future
    }
  }

}


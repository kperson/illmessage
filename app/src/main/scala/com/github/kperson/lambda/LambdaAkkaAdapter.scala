package com.github.kperson.lambda

import java.io.{InputStream, OutputStream, OutputStreamWriter}
import java.nio.charset.StandardCharsets

import akka.http.scaladsl.server
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.server.{LambdaRequestContextImpl, RejectionHandler}
import akka.http.scaladsl.server.RouteResult.{Complete, Rejected}
import akka.stream.ActorMaterializer
import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}
import com.github.kperson.serialization.JSONFormats
import org.json4s.Formats
import org.json4s.jackson.Serialization._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Success


case class LambdaHttpResponse(statusCode: Int, body: String, headers: Map[String, String])

trait LambdaAkkaAdapter extends RequestStreamHandler {

  def route: server.Route
  def actorMaterializer: ActorMaterializer

  def handleRequest(input: InputStream, output: OutputStream, context: Context) {
    implicit val formats: Formats = JSONFormats.formats

    val amazonRequest = read[LambdaHttpRequest](input)
    val request = new LambdaRequestContextImpl(amazonRequest.normalize(), actorMaterializer)

    route(request).onComplete {
      case Success(Rejected(l)) =>
        RejectionHandler.default(l) match {
          case Some(rejectHandler) =>
            rejectHandler(request).onComplete {
              case Success(Complete(res)) => complete(res, output)(actorMaterializer)

              case _ => complete(HttpResponse(500), output)(actorMaterializer)
            }
          case _ => complete(HttpResponse(500), output)(actorMaterializer)
        }
      case Success(Complete(res)) => complete(res, output)(actorMaterializer)
      case _ => complete(HttpResponse(500), output)(actorMaterializer)
    }
  }

  private def complete(response: HttpResponse, output: OutputStream)(implicit materializer: ActorMaterializer) {
    implicit val formats: Formats = JSONFormats.formats
    val bodyFuture = response.entity.dataBytes.runFold(""){ (prev, b) =>
      prev + b.utf8String
    }

    val headers = response.headers.toList.filter { header =>
      header.lowercaseName() != "content-type"
    }.map { header =>
      (header.name, header.value())
    }.toMap + ("Content-Type" -> response.entity.contentType.toString())

    bodyFuture.foreach { body =>
      val lambdaResponse = LambdaHttpResponse(response.status.intValue, body, headers)
      println(write(lambdaResponse))
      val writer = new OutputStreamWriter(out, StandardCharsets.UTF_8.name)
      writer.write(write(lambdaResponse))
      writer.flush()
      writer.close()

      output.flush()
      output.close()
    }
  }

}
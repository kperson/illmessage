package com.github.kperson.lambda

import akka.http.scaladsl.server
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.server.{LambdaRequestContextImpl, RejectionHandler}
import akka.http.scaladsl.server.RouteResult.{Complete, Rejected}
import akka.stream.ActorMaterializer

import java.io._
import java.nio.charset.StandardCharsets

import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}
import com.github.kperson.serialization.JSONFormats

import org.json4s.Formats
import org.json4s.jackson.Serialization._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._


case class LambdaHttpResponse(statusCode: Int, body: String, headers: Map[String, String])

trait LambdaAkkaAdapter extends RequestStreamHandler {

  val route: server.Route
  implicit val actorMaterializer: ActorMaterializer

  import actorMaterializer.executionContext


  def handleRequest(input: InputStream, output: OutputStream, context: Context) {
    implicit val formats: Formats = JSONFormats.formats

    val amazonRequest = read[LambdaHttpRequest](input)
    val request = new LambdaRequestContextImpl(amazonRequest.normalize(), actorMaterializer)

    try {
      val f = route(request).flatMap {
        case Rejected(l) =>
          RejectionHandler.default(l) match {
            case Some(rejectHandler) =>
              rejectHandler(request).flatMap {
                case Complete(res) => complete(res)
                case _ => complete(HttpResponse(500))
              }
            case _ => complete(HttpResponse(500))
          }
        case Complete(res) => complete(res)
      }.recoverWith {
        case _ => complete(HttpResponse(500))
      }
      val str = Await.result(f, 20.seconds)
      output.write(str.getBytes(StandardCharsets.UTF_8))

    }
    catch {
      case ex: Throwable =>
        val errors = new StringWriter()
        ex.printStackTrace(new PrintWriter(errors))
        println(errors.toString)
        val f = complete(HttpResponse(500))
        val str = Await.result(f, 20.seconds)
        output.write(str.getBytes(StandardCharsets.UTF_8))
    }
  }

  private def complete(response: HttpResponse): Future[String] = {
    implicit val formats: Formats = JSONFormats.formats
    val bodyFuture = response.entity.dataBytes.runFold(""){ (prev, b) =>
      prev + b.utf8String
    }

    val headers = response.headers.toList.filter { header =>
      header.lowercaseName() != "content-type"
    }.map { header =>
      (header.name, header.value())
    }.toMap + ("Content-Type" -> response.entity.contentType.toString())

    bodyFuture.map { body =>
      val lambdaResponse = LambdaHttpResponse(response.status.intValue, body, headers)
      println(lambdaResponse)
      write(lambdaResponse)
    }
  }

}
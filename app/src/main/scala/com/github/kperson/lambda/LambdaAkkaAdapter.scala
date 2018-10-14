package com.github.kperson.lambda

import java.io._
import java.nio.charset.StandardCharsets

import akka.http.scaladsl.server
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.server.{LambdaRequestContextImpl, RejectionHandler}
import akka.http.scaladsl.server.RouteResult.{Complete, Rejected}
import akka.stream.ActorMaterializer
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler, RequestStreamHandler}
import com.github.kperson.serialization.JSONFormats
import org.json4s.Formats
import org.json4s.jackson.Serialization._

import scala.concurrent.{Await, Future}
import scala.util.Success
import scala.concurrent.duration._


case class LambdaHttpResponse(statusCode: Int, body: String, headers: Map[String, String])

trait LambdaAkkaAdapter extends RequestHandler[String, String] {

  val route: server.Route
  val actorMaterializer: ActorMaterializer

  import actorMaterializer.executionContext



  def handleRequest(input: String, context: Context): String = {
    implicit val formats: Formats = JSONFormats.formats

    val amazonRequest = read[LambdaHttpRequest](input)
    val request = new LambdaRequestContextImpl(amazonRequest.normalize(), actorMaterializer)

    try {
      val f = route(request).flatMap {
        case Rejected(l) =>
          println("8....................................")
          RejectionHandler.default(l) match {
            case Some(rejectHandler) =>
              println("9....................................")
              rejectHandler(request).flatMap {
                case Complete(res) =>
                  println("2....................................")
                  complete(res)(actorMaterializer)

                case _ =>
                  println("3....................................")
                  complete(HttpResponse(500))(actorMaterializer)
              }
            case _ =>
              println("4....................................")
              complete(HttpResponse(500))(actorMaterializer)
          }
        case Complete(res) =>
          println("5....................................")
          println(res)
          println(actorMaterializer)
          complete(res)(actorMaterializer)
      }.recoverWith { case _ =>
        println("6....................................")
        complete(HttpResponse(500))(actorMaterializer)
      }
      Await.result(f, 20.seconds)


    }
    catch {
      case ex: Throwable =>
        println("ERROR..................................")
        val errors = new StringWriter()
        ex.printStackTrace(new PrintWriter(errors))
        println(errors.toString)
        val f = complete(HttpResponse(500))(actorMaterializer)
        Await.result(f, 20.seconds)
    }
  }

  private def complete(response: HttpResponse)(implicit materializer: ActorMaterializer): Future[String] = {
    import materializer.executionContext
    println("7....................................")
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
      println("HERE....................................................................")
      val lambdaResponse = LambdaHttpResponse(response.status.intValue, body, headers)
      println(lambdaResponse)
      println("WRITING.......")
      write(lambdaResponse)
    }
  }

}
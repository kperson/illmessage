package com.github.kperson.lambda

import java.io._
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

import scala.util.Success


case class LambdaHttpResponse(statusCode: Int, body: String, headers: Map[String, String])

trait LambdaAkkaAdapter extends RequestStreamHandler {

  val route: server.Route
  val actorMaterializer: ActorMaterializer

  import actorMaterializer.executionContext


  def handleRequest(input: InputStream, output: OutputStream, context: Context) {
    implicit val formats: Formats = JSONFormats.formats

    val source = scala.io.Source.fromInputStream(input).mkString

    val amazonRequest = read[LambdaHttpRequest](source)
    val request = new LambdaRequestContextImpl(amazonRequest.normalize(), actorMaterializer)

    try {
      route(request).onComplete {
        case Success(Rejected(l)) =>
          println("8....................................")
          RejectionHandler.default(l) match {
            case Some(rejectHandler) =>
              println("9....................................")
              rejectHandler(request).onComplete {
                case Success(Complete(res)) =>
                  println("2....................................")
                  complete(res, output)(actorMaterializer)

                case _ =>
                  println("3....................................")
                  complete(HttpResponse(500), output)(actorMaterializer)
              }
            case _ =>
              println("4....................................")
              complete(HttpResponse(500), output)(actorMaterializer)
          }
        case Success(Complete(res)) =>
          println("5....................................")
          println(res)
          println(actorMaterializer)
          complete(res, output)(actorMaterializer)
        case _ =>
          println("6....................................")
          complete(HttpResponse(500), output)(actorMaterializer)
      }
    }
    catch {
      case ex: Throwable =>
        println("ERROR..................................")
        val errors = new StringWriter()
        ex.printStackTrace(new PrintWriter(errors))
        println(errors.toString)
        complete(HttpResponse(500), output)(actorMaterializer)
    }
  }

  private def complete(response: HttpResponse, output: OutputStream)(implicit materializer: ActorMaterializer) {
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

    bodyFuture.foreach { body =>
      println("HERE....................................................................")
      val lambdaResponse = LambdaHttpResponse(response.status.intValue, body, headers)
      println(lambdaResponse)
      println("WRITING.......")
      println(write(lambdaResponse))
      output.write(write(lambdaResponse).getBytes(StandardCharsets.UTF_8))
      output.flush()
      output.close()
//      val writer = new OutputStreamWriter(output, StandardCharsets.UTF_8.name)
//      writer.write(write(lambdaResponse))
//      writer.flush()
//      writer.close()
//
//      output.flush()
//      output.close()
    }
  }

}
package com.github.kperson.lambda

import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}

import com.github.kperson.serialization._

import java.io._
import java.nio.charset.StandardCharsets

import play.api.libs.json.{Json, Reads, Writes}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._


case class LambdaHttpResponse(statusCode: Int, body: String = "", headers: Map[String, String] = Map.empty)

trait LambdaAkkaAdapter extends RequestStreamHandler {


  val route: RequestHandler
  implicit val ec: ExecutionContext


  def handleRequest(input: InputStream, output: OutputStream, context: Context) {

    implicit val reads: Reads[LambdaHttpRequest] = Json.reads[LambdaHttpRequest]
    implicit val writes: Writes[LambdaHttpResponse] = Json.writes[LambdaHttpResponse]



    val amazonRequest = Json.fromJson[LambdaHttpRequest](Json.parse(input)).get.normalize()
    val finalRoute: RequestHandler = route orElse {
      case _ => Future.successful(LambdaHttpResponse(404, "request not found", Map("Content-Type" -> "text/plain")))
    }
    val future = finalRoute((amazonRequest.httpMethod, amazonRequest.path, amazonRequest))
      .recover { case _: Throwable =>
        LambdaHttpResponse(500, "un unknown error has occurred")
      }

    val rs = Await.result(future, 30.seconds)
    output.write(Json.toJson(rs).toString().getBytes(StandardCharsets.UTF_8))
    output.flush()
  }

}
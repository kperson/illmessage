package com.github.kperson.aws.dynamo

import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}

import java.io.{InputStream, OutputStream}

import play.api.libs.json._

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._


trait StreamChangeCaptureHandler extends RequestStreamHandler {

  def handleChange(change: ChangeCapture[DynamoMap])

  def handleRequest(input: InputStream, output: OutputStream, context: Context) {

    Json.parse(input) match {
      case StreamChangeCapture(list) =>
        list.foreach { i =>
          handleChange(i)
        }
      case _ =>
    }
    output.flush()
    output.close()
  }

}

trait AsyncStreamChangeCaptureHandler extends RequestStreamHandler {

  implicit val ec: ExecutionContext

  def handleChange(change: ChangeCapture[DynamoMap]): Future[Any]

  def handleRequest(input: InputStream, output: OutputStream, context: Context) {

    val f = Json.parse(input) match {
      case StreamChangeCapture(list) =>
        val futures = list.map { i =>
          handleChange(i)
        }
        Future.sequence(futures)
      case _ =>
        Future.successful(true)
    }
    Await.result(f, 3.minutes)
    output.flush()
    output.close()
  }

}


object StreamChangeCapture {

  def unapply(value: JsValue): Option[List[ChangeCapture[DynamoMap]]] = {
    try {
      val records = (value \ "Records").get.as[JsArray].value
      Some(records.map { record =>
        val eventName = (record \ "eventName").get.as[String]
        val source = (record \ "eventSourceARN").get.as[String]
        if (eventName == "INSERT") {
          val image = (record \ "dynamodb" \ "NewImage").get.as[JsObject]
          New(source, DynamoMap(parseImage(image)))
        }
        else if (eventName == "REMOVE") {
          val image = (record \ "dynamodb" \ "OldImage").get.as[JsObject]
          Delete(source, DynamoMap(parseImage(image)))
        }
        else if(eventName == "MODIFY") {
          val oldImage = DynamoMap(parseImage((record \ "dynamodb" \ "OldImage").get.as[JsObject]))
          val newImage = DynamoMap(parseImage((record \ "dynamodb" \ "NewImage").get.as[JsObject]))
          Update(source, oldImage, newImage)
        }
        else {
          throw new IllegalArgumentException(s"'$eventName' is unsupported event name")
        }
      }.toList)
    }
    catch {
      case _: java.lang.ClassCastException => None
      case _: IllegalArgumentException => None
    }
  }

  private def parseImage(value: JsObject): Map[String, DynamoPrimitive] = {
    value.value.map { case (k, v) => (k, DynamoPrimitive.fromJValue(v)) }.toMap
  }

}
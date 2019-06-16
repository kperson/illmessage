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
    println(value.toString())
    try {
      val records = (value \ "Records").asInstanceOf[JsArray].value
      Some(records.map { record =>
        val eventName = (record \ "eventName").asInstanceOf[JsString].value
        val source = (record \ "eventSourceARN").asInstanceOf[JsString].value
        if (eventName == "INSERT") {
          val image = (record \ "dynamodb" \ "NewImage").asInstanceOf[JsObject]
          New(source, DynamoMap(parseImage(image)))
        }
        else if (eventName == "REMOVE") {
          val image = (record \ "dynamodb" \ "OldImage").asInstanceOf[JsObject]
          Delete(source, DynamoMap(parseImage(image)))
        }
        else if(eventName == "MODIFY") {
          val oldImage = DynamoMap(parseImage((record \ "dynamodb" \ "OldImage").asInstanceOf[JsObject]))
          val newImage = DynamoMap(parseImage((record \ "dynamodb" \ "NewImage").asInstanceOf[JsObject]))
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
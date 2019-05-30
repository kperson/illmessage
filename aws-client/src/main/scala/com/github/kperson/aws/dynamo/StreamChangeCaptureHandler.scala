package com.github.kperson.aws.dynamo

import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}
import java.io.{InputStream, OutputStream}

import org.json4s.{Formats, NoTypeHints}
import org.json4s.jackson.JsonMethods._
import org.json4s.JsonAST.{JArray, JObject, JString, JValue}
import org.json4s.jackson.Serialization

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._


trait StreamChangeCaptureHandler extends RequestStreamHandler {

  def handleChange(change: ChangeCapture[DynamoMap])

  def handleRequest(input: InputStream, output: OutputStream, context: Context) {
    implicit val formats: Formats = Serialization.formats(NoTypeHints)

    parse(input) match {
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
    implicit val formats: Formats = Serialization.formats(NoTypeHints)

    val f = parse(input) match {
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

  def unapply(value: JValue): Option[List[ChangeCapture[DynamoMap]]] = {
    try {
      val records = (value \ "Records").asInstanceOf[JArray]
      Some(records.arr.map { record =>
        val eventName = (record \ "eventName").asInstanceOf[JString].s
        val source = (record \ "eventSourceARN").asInstanceOf[JString].s
        if (eventName == "INSERT") {
          val image = (record \ "dynamodb" \ "NewImage").asInstanceOf[JObject]
          New(source, DynamoMap(parseImage(image)))
        }
        else if (eventName == "REMOVE") {
          val image = (record \ "dynamodb" \ "OldImage").asInstanceOf[JObject]
          Delete(source, DynamoMap(parseImage(image)))
        }
        else if(eventName == "MODIFY") {
          val oldImage = DynamoMap(parseImage((record \ "dynamodb" \ "OldImage").asInstanceOf[JObject]))
          val newImage = DynamoMap(parseImage((record \ "dynamodb" \ "NewImage").asInstanceOf[JObject]))
          Update(source, oldImage, newImage)
        }
        else {
          throw new IllegalArgumentException(s"'$eventName' is unsupported event name")
        }
      })
    }
    catch {
      case _: java.lang.ClassCastException => None
      case _: IllegalArgumentException => None
    }
  }

  private def parseImage(value: JObject): Map[String, DynamoPrimitive] = {
    value.obj.map { case (k, v) => (k, DynamoPrimitive.fromJValue(v)) }.toMap
  }

}
package com.github.kperson.wal

import com.github.kperson.model.Message
import com.github.kperson.lambda._
import com.github.kperson.serialization._

import java.util.UUID

import play.api.libs.json._

import scala.concurrent.ExecutionContext
import trail._


case class MessagePayload(
  routingKey: String,
  body: String,
  exchange: String,
  groupId: Option[String] = None
 ) {

  def toMessage = Message(
    routingKey,
    body,
    exchange,
    groupId.getOrElse(UUID.randomUUID().toString.replace("-", ""))
  )

}


trait WriteAheadAPI {

  def writeAheadDAO: WriteAheadDAO
  implicit val ec: ExecutionContext

  private val writeAheadMatch = Root / "messages"


  val writeAheadRoute: RequestHandler = {
    case (POST, writeAheadMatch(_), req) =>
      val messages = Json.fromJson[List[MessagePayload]]( Json.parse(req.bodyInputStream)).get.map  { _.toMessage }
      writeAheadDAO.write(messages).map { _ =>
        LambdaHttpResponse(204)
      }
  }

}
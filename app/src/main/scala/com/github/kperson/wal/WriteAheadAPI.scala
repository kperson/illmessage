package com.github.kperson.wal

import akka.http.scaladsl.server
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._

import com.github.kperson.model.Message
import com.github.kperson.api.MarshallingSupport

import java.util.UUID


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


trait WriteAheadAPI extends MarshallingSupport {

  def writeAheadDAO: WriteAheadDAO

  val writeAheadRoute: server.Route = {
    path("messages") {
      post {
        decodeRequest {
          entity(as[List[MessagePayload]]) { messagePayloads =>
            val messages = messagePayloads.map { _.toMessage }
            onSuccess(writeAheadDAO.write(messages)) { _ =>
              complete((StatusCodes.OK, messages))
            }
          }
        }
      }
    }
  }

}
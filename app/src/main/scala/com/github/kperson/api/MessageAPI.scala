package com.github.kperson.api

import akka.http.scaladsl.server
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._

import com.github.kperson.model.Message
import com.github.kperson.wal.WAL

import java.util.UUID


case class MessagePayload(
  routingKey: String,
  body: String,
  exchange: String,
  delayInSeconds: Option[Int] = None,
  groupId: Option[String] = None
) {

  def toMessage = Message(
    routingKey,
    body,
    exchange,
    delayInSeconds,
    groupId.getOrElse(UUID.randomUUID().toString.replace("-", ""))
  )

}


trait MessageAPI extends MarshallingSupport {

  def wal: WAL
  def messageRoute: server.Route = {
    path("messages") {
      post {
        decodeRequest {
          entity(as[List[MessagePayload]]) { messagePayloads =>
            val messages = messagePayloads.map { _.toMessage }
            println("hello")
            onSuccess(wal.write(messages)) { _ =>
              complete((StatusCodes.OK, messages))
            }
          }
        }
      }
    }
  }

}
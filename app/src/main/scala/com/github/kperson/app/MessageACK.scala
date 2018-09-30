package com.github.kperson.app

import com.github.kperson.routing.ACKCallback
import com.github.kperson.wal.WAL

import scala.concurrent.Future

class MessageACK(wal: WAL) extends ACKCallback[String] {

  def ack(messageId: String, context: String): Future[Any] = {
    wal.remove(messageId)
  }

}

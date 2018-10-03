package com.github.kperson.app

import com.github.kperson.routing.ACKCallback
import com.github.kperson.wal.WAL

import scala.concurrent.Future

class MessageACK(wal: WAL) extends ACKCallback {

  def ack(messageId: String): Future[Any] = {
    wal.remove(messageId)
  }

}

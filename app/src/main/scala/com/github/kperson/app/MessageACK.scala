package com.github.kperson.app

import com.github.kperson.wal.WAL

import scala.concurrent.Future

class MessageACK(wal: WAL) {

  def ack(messageId: String, partitionKey: String): Future[Any] = {
    wal.remove(messageId, partitionKey)
  }

}

package com.github.kperson.processor

import com.github.kperson.api.APIInit
import com.github.kperson.wal.WALRecord

import scala.concurrent.Future

class MessageProcessorImpl extends MessageProcessor with APIInit {

  def removeWALRecord(record: WALRecord): Future[Any] = {
    wal.remove(record.messageId, record.message.partitionKey)
  }

}

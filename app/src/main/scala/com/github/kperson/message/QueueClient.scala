package com.github.kperson.message

import com.github.kperson.model.MessageSubscription
import com.github.kperson.wal.WALRecord

import scala.concurrent.Future

trait QueueClient {

  def sendMessages(subscriptions: List[MessageSubscription], record: WALRecord): Future[Any]

}

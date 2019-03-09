package com.github.kperson.delivery

import com.github.kperson.model.MessageSubscription
import com.github.kperson.wal.WALRecord

import scala.concurrent.Future

trait DeliveryClient {

  def queueMessages(subscriptions: List[MessageSubscription], record: WALRecord): Future[Any]

}

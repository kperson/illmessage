package com.github.kperson.deadletter

import com.github.kperson.model.{Message, MessageSubscription}

import scala.concurrent.Future


case class DeadLetterMessage(
  subscription: MessageSubscription,
  messageId: String,
  message: Message,
  insertedAt: Long,
  ttl: Long,
  reason: String
)


trait DeadLetterQueueDAO {

  def write(message: DeadLetterMessage): Future[Any]

  def loadToWAL(subscription: MessageSubscription): Future[List[(String, Message)]]

}

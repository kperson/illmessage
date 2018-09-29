package com.github.kperson.dao

import com.github.kperson.model.{Message, MessageSubscription}

import scala.concurrent.Future

trait DAO {

  def fetchSubscriptions: Future[Map[String, List[MessageSubscription]]]

  def deliverMessage(message: Message, subscription: MessageSubscription): Future[Any]

  def intakeMessages(messages: List[Message]): Future[Any]

  def popDeadLetterMessages(subscription: MessageSubscription): Future[List[Message]]

}

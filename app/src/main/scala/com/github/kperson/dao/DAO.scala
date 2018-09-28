package com.github.kperson.dao

import com.github.kperson.model.{Message, Subscription}

import scala.concurrent.Future

trait DAO {

  def fetchSubscriptions: Future[Map[String, List[Subscription]]]

  def deliverMessage(message: Message, subscription: Subscription): Future[Any]

  def intakeMessages(messages: List[Message]): Future[Any]

  def popDeadLetterMessages(subscription: Subscription): Future[List[Message]]

}

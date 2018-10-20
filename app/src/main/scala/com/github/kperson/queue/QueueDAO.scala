package com.github.kperson.queue

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

trait QueueDAO {

  def sendMessage(
    queueName: String,
    messageBody: String,
    subscriptionId: String,
    messageId: String,
    accountId: String,
    delay: Option[FiniteDuration]
  ): Future[Any]

}

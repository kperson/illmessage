package com.github.kperson.queue

import com.github.kperson.aws.sqs.SQSClient

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class AmazonQueueDAO(sqsClient: SQSClient) extends QueueDAO {

  def sendMessage(
   queueName: String,
   messageBody: String,
   subscriptionId: String,
   messageId: String,
   accountId: String,
   delay: Option[FiniteDuration]
  ): Future[Any] = {
    sqsClient.sendMessage(
      queueName,
      messageBody,
      delay,
      if (queueName.endsWith(".fifo")) Some(messageId) else None,
      if (queueName.endsWith(".fifo")) Some(subscriptionId) else None,
      Some(accountId)
    )
  }

}

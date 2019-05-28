package com.github.kperson.message

import com.github.kperson.aws.sqs.SQSClient
import com.github.kperson.model.MessageSubscription
import com.github.kperson.wal.WALRecord

import scala.concurrent.{ExecutionContext, Future}


class AmazonQueueClient(
  sqsClient: SQSClient,
)(implicit ec: ExecutionContext) extends QueueClient {

  def sendMessages(subscriptions: List[MessageSubscription], record: WALRecord): Future[Any] = {

    val subscriptionsPerQueue = subscriptions
      .groupBy { sub => (sub.accountId, sub.queue) }
      .map { case (_, l) => l.head }
      .toList
    val sends: List[Future[Any]] = subscriptionsPerQueue.map { sub =>
      sendMessage(
        sub.queue,
        record.message.body,
        sub.id,
        record.messageId,
        sub.accountId
      )
    }
    if (sends.nonEmpty) Future.sequence(sends) else Future.successful(true)
  }

  private def sendMessage(
   queueName: String,
   messageBody: String,
   subscriptionId: String,
   messageId: String,
   accountId: String
  ): Future[Any] = {
    sqsClient.sendMessage(
      queueName,
      messageBody,
      None,
      if (queueName.endsWith(".fifo")) Some(messageId) else None,
      if (queueName.endsWith(".fifo")) Some(subscriptionId) else None,
      Some(accountId)
    )
  }

}

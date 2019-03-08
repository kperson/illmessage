package com.github.kperson.message

import com.github.kperson.aws.sqs.SQSClient
import com.github.kperson.deadletter.{DeadLetterMessage, DeadLetterQueueDAO}
import com.github.kperson.model.MessageSubscription
import com.github.kperson.wal.WALRecord
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

class AmazonQueueClient(
  sqsClient: SQSClient,
  deadLetterQueueDAO: DeadLetterQueueDAO
)(implicit ec: ExecutionContext) extends QueueClient {

  def sendMessages(subscriptions: List[MessageSubscription], record: WALRecord): Future[Any] = {

    val logger: Logger = LoggerFactory.getLogger(getClass)

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
        sub.accountId,
        record.message.delayInSeconds.map { _.seconds }
      ).recoverWith { case ex: Throwable =>
        logger.warn(s"dead letter occurred for subscription id = ${sub.id}, message id = ${record.messageId}")
        deadLetterQueueDAO.write(
          DeadLetterMessage(
            sub,
            record.messageId,
            record.message,
            System.currentTimeMillis(),
            System.currentTimeMillis() / 1000L + 14.days.toSeconds,
            ex.getMessage
          )
        )
      }
    }
    if (sends.nonEmpty) Future.sequence(sends) else Future.successful(true)
  }

  private def sendMessage(
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

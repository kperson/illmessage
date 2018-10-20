package com.github.kperson.processor

import com.github.kperson.aws.dynamo._
import com.github.kperson.deadletter.{DeadLetterMessage, DeadLetterQueueDAO}
import com.github.kperson.model.MessageSubscription
import com.github.kperson.queue.QueueDAO
import com.github.kperson.serialization.JSONFormats
import com.github.kperson.subscription.SubscriptionDAO
import com.github.kperson.wal.{WALRecord, WriteAheadDAO}

import org.json4s.jackson.Serialization._
import org.json4s.Formats
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._


trait MessageProcessor extends StreamChangeCaptureHandler {

  implicit val formats: Formats = JSONFormats.formats
  implicit val ec: ExecutionContext

  def subscriptionDAO: SubscriptionDAO
  def walDAO: WriteAheadDAO
  def queueDAO: QueueDAO
  def deadLetterQueueDAO: DeadLetterQueueDAO

  val logger: Logger = LoggerFactory.getLogger(getClass)

  def handleChange(change: ChangeCapture[DynamoMap]): Unit = {
    logger.debug(s"processing change, $change")
    change.map { _.flatten } match {
      case New(_, item) =>
        val record = read[WALRecord](write(item))
        logger.info(s"received new record, $record")
        val f = for {
          _ <-  walDAO.remove(record.messageId, record.message.partitionKey)
          allSubscriptions <- {
            record.preComputedSubscription
              .map { x => Future.successful(List(x)) }
              .getOrElse(subscriptionDAO.fetchSubscriptions(record.message.exchange, record.message.routingKey))
          }
          rs <- sendMessages(allSubscriptions, record)
        } yield rs
        Await.result(f, 30.seconds)
      case _ =>
        logger.debug("ignoring change")
    }
  }

  def sendMessages(subscriptions: List[MessageSubscription], record: WALRecord): Future[Any] = {
    val subscriptionsPerQueue = subscriptions
      .groupBy { sub => (sub.accountId, sub.queue) }
      .map { case (_, l) => l.head }
      .toList
    val sends: List[Future[Any]] = subscriptionsPerQueue.map { sub =>
      queueDAO.sendMessage(
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

}

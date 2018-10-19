package com.github.kperson.processor

import com.github.kperson.aws.dynamo._
import com.github.kperson.model.MessageSubscription
import com.github.kperson.serialization.JSONFormats
import com.github.kperson.wal.WALRecord

import org.json4s.jackson.Serialization._
import org.json4s.Formats
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._


trait MessageProcessorDependencies {

  def removeWALRecord(record: WALRecord): Future[Any]
  def sendMessage(
   queueName: String,
   messageBody: String,
   delay: Option[FiniteDuration] = None,
   messageDeduplicationId: Option[String] = None,
   messageGroupId: Option[String] = None,
   messageAccountId: Option[String] = None
 ): Future[Any]


  def saveDeadLetter(record: WALRecord, subscription: MessageSubscription, reason: String): Future[Any]

  def fetchSubscriptions(exchange: String, routingKey: String): Future[List[MessageSubscription]]

}


trait MessageProcessor extends StreamChangeCaptureHandler with MessageProcessorDependencies {

  implicit val formats: Formats = JSONFormats.formats
  implicit val ec: ExecutionContext

  val logger: Logger = LoggerFactory.getLogger(getClass)

  def handleChange(change: ChangeCapture[DynamoMap]) {
    logger.debug(s"processing change, $change")
    change.map { _.flatten } match {
      case New(_, item) =>
        val record = read[WALRecord](write(item))
        logger.info(s"received new record, $record")
        val f = fetchSubscriptions(record.message.exchange, record.message.routingKey).flatMap { allSubscriptions =>
          val sends = allSubscriptions.map { sub =>
            sendMessage(
              sub.queue,
              record.message.body,
              record.message.delayInSeconds.map { _.seconds },
              if(sub.queue.endsWith(".fifo")) Some(record.messageId) else None,
              if(sub.queue.endsWith(".fifo")) Some(sub.id) else None,
              Some(sub.accountId)
            ).recoverWith { case ex: Throwable =>
              saveDeadLetter(record, sub, ex.getMessage)
            }
          }
          if(sends.nonEmpty) Future.sequence(sends) else Future.successful(true)
        }.flatMap { _ =>
          removeWALRecord(record)
        }
        Await.result(f, 30.seconds)
      case _ =>
        logger.debug("ignoring change")
    }
  }

}

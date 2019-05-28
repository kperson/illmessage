package com.github.kperson.wal

import com.github.kperson.app.AppInit
import com.github.kperson.aws.dynamo._
import com.github.kperson.delivery.DeliveryDAO
import com.github.kperson.serialization.JSONFormats
import com.github.kperson.subscription.SubscriptionDAO

import org.json4s.Formats
import org.json4s.jackson.Serialization._
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}


trait WriteAheadStreamProcessor extends StreamChangeCaptureHandler {

  implicit val formats: Formats = JSONFormats.formats
  implicit val ec: ExecutionContext

  def subscriptionDAO: SubscriptionDAO
  def walDAO: WriteAheadDAO
  def deliveryDAO: DeliveryDAO

  val logger: Logger = LoggerFactory.getLogger(getClass)

  def handleChange(change: ChangeCapture[DynamoMap]) {
    logger.debug(s"processing change, $change")
    val item = change.map { _.flatten } match {
      case New(_, payload) => Some(read[WALRecord](write(payload)))
      case _ => None
    }
    item.foreach { record =>
      Await.result(handleNewWALRecord(record), 30.seconds)
    }
  }

  def handleNewWALRecord(record: WALRecord): Future[Any] = {
    logger.debug(s"received new record, $record")
    val enqueue = for {
      allSubscriptions <- {
          subscriptionDAO.fetchSubscriptions(record.message.exchange, record.message.routingKey)
      }
      rs <- deliveryDAO.queueMessages(allSubscriptions, record)
    } yield rs
    enqueue.flatMap { _ => walDAO.remove(record.messageId, record.message.partitionKey) }
  }
}

//concrete implementation
class WriteAheadStreamProcessorImpl extends WriteAheadStreamProcessor with AppInit
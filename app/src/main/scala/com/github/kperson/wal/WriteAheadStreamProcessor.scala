package com.github.kperson.wal

import com.github.kperson.app.AppInit
import com.github.kperson.aws.dynamo._
import com.github.kperson.delivery.DeliveryClient
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
  def deliveryClient: DeliveryClient

  val logger: Logger = LoggerFactory.getLogger(getClass)

  def handleChange(change: ChangeCapture[DynamoMap]) {
    logger.debug(s"processing change, $change")
    val item = change.map { _.flatten } match {
      case New(_, item) => Some(read[WALRecord](write(item)))
      case _ => None
    }
    item.foreach { record =>
      Await.result(handleNewWALRecord(record), 30.seconds)
    }
  }

  def handleNewWALRecord(record: WALRecord): Future[Any] = {
    logger.info(s"received new record, $record")
    for {
      //1. remove the record from the WAL
      _ <- walDAO.remove(record.messageId, record.message.partitionKey)
      allSubscriptions <- {
        //2. if we have a pre computed subscription, fetch that, otherwise, look up all applicable subscriptions
        record.preComputedSubscription
          .map { preComputed => Future.successful(List(preComputed)) }
          .getOrElse(subscriptionDAO.fetchSubscriptions(record.message.exchange, record.message.routingKey))
      }
      //3. send out the messages
      rs <- deliveryClient.queueMessages(allSubscriptions, record.copy(preComputedSubscription = None))
    } yield rs
  }
}

//concrete implementation
class WriteAheadStreamProcessorImpl extends WriteAheadStreamProcessor with AppInit
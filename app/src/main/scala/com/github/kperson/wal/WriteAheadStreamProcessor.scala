package com.github.kperson.wal

import com.github.kperson.app.AppInit
import com.github.kperson.aws.dynamo._
import com.github.kperson.delivery.DeliveryDAO
import com.github.kperson.subscription.SubscriptionDAO

import com.github.kperson.serialization._


import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}


trait WriteAheadStreamProcessor extends StreamChangeCaptureHandler {

  implicit val ec: ExecutionContext

  def subscriptionDAO: SubscriptionDAO
  def walDAO: WriteAheadDAO
  def deliveryDAO: DeliveryDAO


  def handleChange(change: ChangeCapture[DynamoMap]) {
    val item = change.map { _.flatten } match {
      case New(_, payload) =>
        Some(readJSON[WALRecord](writeJSON(payload)))
      case _ => None
    }
    item.foreach { record =>
      Await.result(handleNewWALRecord(record), 30.seconds)
    }
  }

  def handleNewWALRecord(record: WALRecord): Future[Any] = {
    val enqueue = for {
      allSubscriptions <- {
        subscriptionDAO.fetchSubscriptions(record.message.exchange, record.message.routingKey)
      }
      rs <- deliveryDAO.queueMessages(allSubscriptions, record)
    } yield rs
    enqueue.flatMap { _ =>
      println(s"removing: ${record}")
      walDAO.remove(record.messageId, record.message.partitionKey)
    }
  }
}

//concrete implementation
class WriteAheadStreamProcessorImpl extends WriteAheadStreamProcessor with AppInit
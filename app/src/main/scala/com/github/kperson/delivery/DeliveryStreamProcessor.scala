package com.github.kperson.delivery

import com.github.kperson.app.AppInit
import com.github.kperson.aws.dynamo._
import com.github.kperson.message.QueueClient
import com.github.kperson.serialization._


import scala.concurrent.{ExecutionContext, Future}


trait DeliveryStreamProcessor extends AsyncStreamChangeCaptureHandler {

  def queueClient: QueueClient
  def deliveryDAO: DeliveryDAO

  implicit val ec: ExecutionContext

  def handleChange(change: ChangeCapture[DynamoMap]): Future[Any] = {
    val item: Option[ChangeCapture[Delivery]] = change.map { _.flatten } match {
      case New(eventSource, payload) =>
        val d = readJSON[Delivery](writeJSON(payload))
        Some(New(eventSource, d))
      case Update(eventSource, oldItem, newItem) =>
        val dOld = readJSON[Delivery](writeJSON(oldItem))
        val dNew = readJSON[Delivery](writeJSON(newItem))
        Some(Update(eventSource, dOld, dNew))
      case _ => None
    }
    item match {
      case Some(delivery) => handleDelivery(delivery)
      case _ => Future.successful(true)
    }
  }

  def handleDelivery(change: ChangeCapture[Delivery]): Future[Any] = {
    val inFlight = "inFlight"
    val delivery = change match {
      case New(_, newDelivery) if newDelivery.status == inFlight => Some(newDelivery)
      case Update(_, oldDelivery, newDelivery) if oldDelivery.status != inFlight && newDelivery.status == inFlight =>
        Some(newDelivery)
      case _ => None
    }

    delivery match {
      case Some(d) =>
        queueClient.sendMessage(d).flatMap { _ =>
          deliveryDAO.remove(d)
        }.recoverWith { case ex: Throwable =>
          deliveryDAO.markDeadLetter(d, ex)
        }
      case _ => Future.successful(true)
    }
  }



}

class DeliveryStreamProcessorImpl extends DeliveryStreamProcessor with AppInit

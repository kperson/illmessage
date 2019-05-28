package com.github.kperson.delivery

import com.github.kperson.app.AppInit
import com.github.kperson.aws.dynamo._
import com.github.kperson.message.QueueClient
import com.github.kperson.serialization.JSONFormats
import org.json4s.Formats
import org.json4s.jackson.Serialization.{read, write}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

trait DeliveryStreamProcessor extends StreamChangeCaptureHandler {


  def queueClient: QueueClient

  implicit val formats: Formats = JSONFormats.formats

  def handleChange(change: ChangeCapture[DynamoMap]) {
    val item = change.map { _.flatten } match {
      case New(_, payload) =>
        val d = read[Delivery](write(payload))
        if(d.status == "inFlight") Some(d) else None
      case Update(_, oldItem, newItem) =>
        val dOld = read[Delivery](write(oldItem))
        val dNew = read[Delivery](write(newItem))
        if(dNew.status == "inFlight" && dOld.status != "inFlight") Some(dNew) else None
      case _ => None
    }
    item.foreach { delivery =>
      Await.result(handleDelivery(delivery), 30.seconds)
    }
  }

  def handleDelivery(delivery: Delivery): Future[Any] = {
    queueClient.sendMessage(delivery)
  }

}

class DeliveryStreamProcessorImpl extends DeliveryStreamProcessor with AppInit

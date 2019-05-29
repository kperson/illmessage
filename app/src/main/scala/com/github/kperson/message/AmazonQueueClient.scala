package com.github.kperson.message

import com.github.kperson.aws.sqs.SQSClient
import com.github.kperson.delivery.Delivery
import com.github.kperson.serialization.JSONFormats

import org.json4s.Formats
import org.json4s.jackson.Serialization.write

import scala.concurrent.{ExecutionContext, Future}


case class FinalDelivery(
  subscriptionId: String,
  groupId: String,
  sequenceId: Long,
  message: String
)

class AmazonQueueClient(
  sqsClient: SQSClient,
)(implicit ec: ExecutionContext) extends QueueClient {

  implicit val formats: Formats = JSONFormats.formats

  def sendMessage(delivery: Delivery): Future[Any] = {
    val targetIsFiFo = delivery.subscription.queue.endsWith(".fifo")
    val finalDelivery = FinalDelivery(
      delivery.subscription.id,
      delivery.message.groupId,
      delivery.sequenceId,
      delivery.message.body
    )
    sqsClient.sendMessage(
      delivery.subscription.queue,
      write(finalDelivery),
      None,
      if (targetIsFiFo) Some(delivery.messageId) else None,
      if (targetIsFiFo) Some(delivery.subscription.id) else None,
      Some(delivery.subscription.accountId)
    )
  }

}

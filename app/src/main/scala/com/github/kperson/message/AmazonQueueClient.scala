package com.github.kperson.message

import com.github.kperson.aws.sqs.SQSClient
import com.github.kperson.delivery.Delivery

import com.github.kperson.serialization._

import scala.concurrent.{ExecutionContext, Future}


case class FinalDelivery(
  subscriptionId: String,
  groupId: String,
  sequenceId: Long,
  message: String,
  apiEndpoint: String
)

class AmazonQueueClient(
  sqsClient: SQSClient,
  apiEndpoint: String
)(implicit ec: ExecutionContext) extends QueueClient {

  def sendMessage(delivery: Delivery): Future[Any] = {
    val targetIsFiFo = delivery.subscription.queue.endsWith(".fifo")
    val finalDelivery = FinalDelivery(
      delivery.subscription.id,
      delivery.message.groupId,
      delivery.sequenceId,
      delivery.message.body,
      apiEndpoint
    )

    sqsClient.sendMessage(
      delivery.subscription.queue,
      writeJSON(finalDelivery),
      None,
      if (targetIsFiFo) Some(delivery.messageId) else None,
      if (targetIsFiFo) Some(delivery.subscription.id) else None,
      Some(delivery.subscription.accountId)
    )
  }

}

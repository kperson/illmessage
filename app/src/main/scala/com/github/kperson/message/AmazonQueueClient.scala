package com.github.kperson.message

import com.github.kperson.aws.sqs.SQSClient
import com.github.kperson.delivery.Delivery
import com.github.kperson.serialization.JSONFormats

import org.json4s.Formats
import org.json4s.jackson.Serialization.write

import scala.concurrent.{ExecutionContext, Future}


class AmazonQueueClient(
  sqsClient: SQSClient,
)(implicit ec: ExecutionContext) extends QueueClient {

  implicit val formats: Formats = JSONFormats.formats

  def sendMessage(delivery: Delivery): Future[Any] = {
    val targetIsFiFo = delivery.subscription.queue.endsWith(".fifo")
    sqsClient.sendMessage(
      delivery.subscription.queue,
      write(delivery),
      None,
      if (targetIsFiFo) Some(delivery.messageId) else None,
      if (targetIsFiFo) Some(delivery.subscription.id) else None,
      Some(delivery.subscription.accountId)
    )
  }

}

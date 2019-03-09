package com.github.kperson.delivery

import com.github.kperson.aws.dynamo.DynamoClient
import com.github.kperson.model.MessageSubscription
import com.github.kperson.serialization.JSONFormats
import com.github.kperson.wal.WALRecord

import java.util.Date

import org.json4s.Formats

import scala.concurrent.{ExecutionContext, Future}


class AmazonDeliveryClient(client: DynamoClient, deliveryTable: String)(implicit ec: ExecutionContext) extends DeliveryClient {

  def queueMessages(subscriptions: List[MessageSubscription], record: WALRecord): Future[Any] = {
    val groupings = subscriptions.grouped(25).toList
    val groupedJobs = groupings.map { queueGroupedMessages(_, record) }
    Future.sequence(groupedJobs)
  }

  private def queueGroupedMessages(subscriptions: List[MessageSubscription], record: WALRecord): Future[Any] = {
    implicit val formats: Formats = JSONFormats.formats
    val deliveries = subscriptions.map { Delivery(record.message, _, new Date()) }
    client.batchPutItems(deliveryTable, deliveries)
  }

}

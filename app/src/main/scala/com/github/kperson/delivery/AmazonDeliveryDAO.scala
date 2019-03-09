package com.github.kperson.delivery

import com.github.kperson.aws.dynamo.DynamoClient
import com.github.kperson.model.MessageSubscription
import com.github.kperson.serialization.JSONFormats
import com.github.kperson.wal.WALRecord
import com.github.kperson.util.Backoff

import java.util.Date

import org.json4s.Formats

import scala.concurrent.Future


class AmazonDeliveryDAO(client: DynamoClient, deliveryTable: String) extends DeliveryDAO {

  import client.ec
  implicit val formats: Formats = JSONFormats.formats


  def queueMessages(subscriptions: List[MessageSubscription], record: WALRecord): Future[List[Delivery]] = {
    val groupings = subscriptions.grouped(25).toList
    val groupedJobs = groupings.map { queueGroupedMessages(_, record) }
    Future.sequence(groupedJobs).map { _.flatten.toList }
  }

  private def queueGroupedMessages(subscriptions: List[MessageSubscription], record: WALRecord): Future[List[Delivery]] = {
    val deliveries = subscriptions.map { Delivery(record.message, _, new Date()) }
    Backoff.runBackoffTask(5, deliveries) { items =>
      client.batchPutItems(deliveryTable, items).map { _.unprocessedInserts }
    }.map { _ => deliveries }
  }

  def remove(delivery: Delivery): Future[Any] = {
    client.deleteItem[Delivery](deliveryTable, Map(
      "subscriptionId" -> delivery.subscription.id,
      "createdAt" -> delivery.createdAt.getTime
    ))
  }

}
package com.github.kperson.delivery

import com.github.kperson.aws.dynamo.DynamoClient
import com.github.kperson.model.MessageSubscription
import com.github.kperson.serialization.JSONFormats
import com.github.kperson.wal.WALRecord
import com.github.kperson.util.Backoff

import java.util.Date

import org.json4s.Formats

import scala.concurrent.Future


class AmazonDeliveryClient(client: DynamoClient, deliveryTable: String) extends DeliveryClient {

  import client.ec

  def queueMessages(subscriptions: List[MessageSubscription], record: WALRecord): Future[Boolean] = {
    val groupings = subscriptions.grouped(25).toList
    val groupedJobs = groupings.map { queueGroupedMessages(_, record) }
    Future.sequence(groupedJobs).map { _ => true }
  }

  private def queueGroupedMessages(subscriptions: List[MessageSubscription], record: WALRecord): Future[Boolean] = {
    implicit val formats: Formats = JSONFormats.formats
    val deliveries = subscriptions.map { Delivery(record.message, _, new Date()) }
    Backoff.runBackoffTask(5, deliveries) { items =>
      client.batchPutItems(deliveryTable, items).map { _.unprocessedInserts }
    }
  }

}
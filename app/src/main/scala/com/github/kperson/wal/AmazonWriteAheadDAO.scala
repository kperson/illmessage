package com.github.kperson.wal

import java.util.UUID

import com.github.kperson.aws.dynamo.DynamoClient
import com.github.kperson.model.{Message, MessageSubscription}
import com.github.kperson.serialization.JSONFormats
import com.github.kperson.util.Backoff

import org.json4s.Formats

import scala.concurrent.Future


case class WALRecord(
  message: Message,
  messageId: String,
  preComputedSubscription: Option[MessageSubscription] = None
)

class AmazonWriteAheadDAO(client: DynamoClient, walTable: String) extends WriteAheadDAO {

  implicit val defaultFormats: Formats = JSONFormats.formats

  import client.ec

  def write(messages: List[Message]): Future[List[String]] = {
    writeWithSubscription(messages.map { (_, None) })
  }

  def writeWithSubscription(messagesSubscriptions: List[(Message, Option[MessageSubscription])]): Future[List[String]] = {
    val transactionGroups = messagesSubscriptions.map { case (message, subscriptions) =>
      val timestamp = "%014d".format(System.currentTimeMillis())
      val randomId = UUID.randomUUID().toString.replace("-", "")
      val messageId = s"$timestamp-$randomId"
      WALRecord(message, messageId, subscriptions)
    }.grouped(25).toList
    Future.sequence(transactionGroups.map { writeWALRecords(_) })
    .map { _ =>
      transactionGroups.flatten.map { _.messageId }
    }
  }

  def remove(messageId: String, partitionKey: String): Future[Any] = {
    client.deleteItem[WALRecord](
      walTable,
      Map(
        "partitionKey" -> partitionKey,
        "messageId" -> messageId
      )
    )
  }

  def fetchWALRecord(messageId: String, partitionKey: String): Future[Option[WALRecord]] = {
    client.getItem[WALRecord](
      walTable,
      Map(
        "partitionKey" -> partitionKey,
        "messageId" -> messageId
      )
    )
  }

  private def writeWALRecords(
    records: List[WALRecord]
  ): Future[Any] = {
    Backoff.runBackoffTask(5, records) { items =>
      client.batchPutItems(walTable, items).map { _.unprocessedInserts }
    }
  }

}
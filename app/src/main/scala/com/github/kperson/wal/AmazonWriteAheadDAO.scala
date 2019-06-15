package com.github.kperson.wal

import java.util.UUID

import com.github.kperson.aws.dynamo.DynamoClient
import com.github.kperson.model.Message
import com.github.kperson.util.Backoff

import scala.concurrent.Future

import com.github.kperson.serialization._

case class WALRecord(
  message: Message,
  messageId: String
)

class AmazonWriteAheadDAO(client: DynamoClient, walTable: String) extends WriteAheadDAO {

  import client.ec

  def write(messages: List[Message]): Future[List[String]] = {
    val transactionGroups = messages.map { message =>
      val timestamp = "%014d".format(System.currentTimeMillis())
      val randomId = UUID.randomUUID().toString.replace("-", "")
      val messageId = s"$timestamp-$randomId"
      WALRecord(message, messageId)
    }.grouped(25).toList
    Future.sequence(transactionGroups.map(writeWALRecords))
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
    Backoff.runBackoffTask(7, 2, records) { items =>
      client.batchPutItems(walTable, items).map { _.unprocessedInserts }
    }
  }

}
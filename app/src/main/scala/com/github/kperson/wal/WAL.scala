package com.github.kperson.wal

import java.util.UUID

import com.github.kperson.aws.dynamo.DynamoClient
import com.github.kperson.model.Message
import org.json4s.NoTypeHints
import org.json4s.jackson.Serialization

import scala.concurrent.Future

case class WALRecord(
  message: Message,
  batchId: String,
  partitionKey: String,
  messageId: String
)

class WAL(client: DynamoClient, walTable: String) {

  implicit val defaultFormats = Serialization.formats(NoTypeHints)

  val partitionKey = "wal_partition_key"

  import client.ec

  def write(messages: List[Message]): Future[List[String]] = {
    val batchId = UUID.randomUUID().toString.replace("-", "")
    val listWithIndex = messages.zip(0L until messages.size.toLong)
    val transactionGroups = listWithIndex.map { case (message, index) =>
      val timestamp = "%016d".format(System.currentTimeMillis())
      val insertNum = "%09d".format(index)
      WALRecord(message, batchId, partitionKey, s"$timestamp-$insertNum-$batchId")
    }.grouped(25).toList
    Future.sequence(transactionGroups.map { writeWALRecords(_) })
    .map { _ =>
      transactionGroups.flatten.map { _.messageId }
    }
  }

  def loadRecords(
    base: List[WALRecord] = List.empty,
    lastEvaluatedKey: Option[Map[String, Any]] = None
  ): Future[List[WALRecord]] = {
    val f = client.query[WALRecord](
      walTable,
      "partitionKey = :partitionKey",
      expressionAttributeValues = Map(":partitionKey" -> partitionKey),
      lastEvaluatedKey = lastEvaluatedKey,
      limit = 300
    )
    f.flatMap { records =>
      val newBase = base ++ records.results
      if(records.results.isEmpty || lastEvaluatedKey.isEmpty) {
        Future.successful(newBase)
      }
      else {
        loadRecords(newBase, lastEvaluatedKey)
      }
    }
  }

  def removeRecord(messageId: String): Future[Any] = {
    client.deleteItem[WALRecord](
      walTable,
      Map(
        "partitionKey" -> partitionKey,
        "messageId" -> messageId
      )
    )
  }

  private def writeWALRecords(records: List[WALRecord]): Future[Unit] = {
    client.batchPutItems(walTable, records).flatMap {
      case rs if rs.unprocessedItems.nonEmpty => writeWALRecords(rs.unprocessedItems)
      case _ => Future.successful(Unit)
    }
  }

}

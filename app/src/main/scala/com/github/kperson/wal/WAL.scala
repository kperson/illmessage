package com.github.kperson.wal

import java.util.{Timer, TimerTask, UUID}

import com.github.kperson.aws.dynamo.DynamoClient
import com.github.kperson.model.Message
import org.json4s.NoTypeHints
import org.json4s.jackson.Serialization

import scala.concurrent.{Future, Promise}
import scala.concurrent.duration._

case class WALRecord(
  message: Message,
  batchId: String,
  partitionKey: String,
  messageId: String
)

class WAL(client: DynamoClient, walTable: String) {

  implicit val defaultFormats = Serialization.formats(NoTypeHints)

  val partitionKey = "wal_partition_key"
  val maxWriteScheduleDelay = 6.seconds

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

  def load(
    base: List[WALRecord] = List.empty,
    lastEvaluatedKey: Option[Map[String, Any]] = None
  ): Future[List[WALRecord]] = {
    val f = client.query[WALRecord](
      walTable,
      "partitionKey = :partitionKey",
      expressionAttributeValues = Map(":partitionKey" -> partitionKey),
      lastEvaluatedKey = lastEvaluatedKey,
      limit = 300,
      consistentRead = true
    )
    f.flatMap { records =>
      val newBase = base ++ records.results
      if(records.results.isEmpty || lastEvaluatedKey.isEmpty) {
        Future.successful(newBase)
      }
      else {
        load(newBase, lastEvaluatedKey)
      }
    }
  }

  def remove(messageId: String): Future[Any] = {
    client.deleteItem[WALRecord](
      walTable,
      Map(
        "partitionKey" -> partitionKey,
        "messageId" -> messageId
      )
    )
  }

  private def writeWALRecords(records: List[WALRecord], delay: FiniteDuration = 200.milliseconds, allowedIterations: Int = 15): Future[Any] = {
    client.batchPutItems(walTable, records).flatMap {
      case rs if rs.unprocessedItems.nonEmpty =>
        if(allowedIterations > 1) {
          //exponential backoff
          val nextDelay = if (delay * 2 > maxWriteScheduleDelay) maxWriteScheduleDelay else delay * 2
          val timer = new Timer()
          val p = Promise[Any]()
          timer.schedule(new TimerTask {
            def run() {
              val f = writeWALRecords(rs.unprocessedItems, nextDelay, allowedIterations = allowedIterations - 1)
              f.onComplete { p.complete(_) }
            }
          }, delay.toMillis)
          p.future
        }
        else {
          Future.failed(
            new RuntimeException(
              "unable to write WAL logs after 15 attempts, " +
              "some messages may not be delivered if this process crashes " +
              "in the next few seconds"
            )
          )
        }
      case _ => Future.successful(true)
    }
  }

}

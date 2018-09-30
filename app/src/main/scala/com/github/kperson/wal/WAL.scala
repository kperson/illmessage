package com.github.kperson.wal

import java.util.{Timer, TimerTask, UUID}

import com.github.kperson.aws.dynamo.DynamoClient
import com.github.kperson.model.{Message, MessageSubscription}
import com.github.kperson.serialization.JSONFormats
import org.json4s.Formats

import scala.concurrent.{Future, Promise}
import scala.concurrent.duration._

class WALTransferMessage(
  val message: Message,
  val mId: String,
  val preComputedSubscription: Option[MessageSubscription],
  val ttl: Long = System.currentTimeMillis + 5.minutes.toMillis
) extends Transfer {

  val messageId = Some(mId)

  val messages = List(message)

  def onTransfer() {
  }

}

case class WALRecord(
  message: Message,
  batchId: String,
  partitionKey: String,
  messageId: String,
  preComputedSubscription: Option[MessageSubscription] = None
)

class WAL(client: DynamoClient, walTable: String) {

  implicit val defaultFormats: Formats = JSONFormats.formats

  private val maxWriteScheduleDelay = 6.seconds
  private var writeNum = 0

  import client.ec


  def write(messages: List[Message]): Future[List[String]] = {
    writeWithSubscription(messages.map { (_, None) })
  }

  def writeWithSubscription(messagesSubscriptions: List[(Message, Option[MessageSubscription])]): Future[List[String]] = {
    writeNum = if(writeNum == 9999999) 0 else writeNum + 1
    val batchId = UUID.randomUUID().toString.replace("-", "")
    val listWithIndex = messagesSubscriptions.zip(0L until messagesSubscriptions.size.toLong)
    val transactionGroups = listWithIndex.map { case ((message, subscriptions), index) =>
      val timestamp = "%014d".format(System.currentTimeMillis())
      val insertNum = "%09d".format(index)
      val writeNumFormat =  "%07d".format(writeNum)
      WALRecord(message, batchId, WAL.partitionKey, s"$timestamp-$writeNumFormat-$insertNum-$batchId", subscriptions)
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
      expressionAttributeValues = Map(":partitionKey" -> WAL.partitionKey),
      lastEvaluatedKey = lastEvaluatedKey,
      limit = 300,
      consistentRead = true
    )
    f.flatMap { records =>
      val newBase = base ++ records.results
      records.lastEvaluatedKey match {
        case key @ Some(_) => load(newBase, key)
        case _ => Future.successful(newBase)
      }
    }
  }

  def remove(messageId: String): Future[Any] = {
    client.deleteItem[WALRecord](
      walTable,
      Map(
        "partitionKey" -> WAL.partitionKey,
        "messageId" -> messageId
      )
    )
  }

  private def writeWALRecords(records: List[WALRecord], delay: FiniteDuration = 200.milliseconds, allowedIterations: Int = 15): Future[Any] = {
    client.batchPutItems(walTable, records).flatMap {
      case rs if rs.unprocessedInserts.nonEmpty =>
        if(allowedIterations > 1) {
          //exponential backoff
          val nextDelay = if (delay * 2 > maxWriteScheduleDelay) maxWriteScheduleDelay else delay * 2
          val timer = new Timer()
          val p = Promise[Any]()
          timer.schedule(new TimerTask {
            def run() {
              val f = writeWALRecords(rs.unprocessedInserts, nextDelay, allowedIterations = allowedIterations - 1)
              f.onComplete { p.complete }
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

object WAL {

  val partitionKey = "wal_partition_key"


}

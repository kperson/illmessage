package com.github.kperson.wal

import java.util.{Timer, TimerTask, UUID}

import com.github.kperson.aws.dynamo.DynamoClient
import com.github.kperson.model.{Message, MessageSubscription}
import com.github.kperson.serialization.JSONFormats
import org.json4s.Formats

import scala.concurrent.{Future, Promise}
import scala.concurrent.duration._


case class WALRecord(
  message: Message,
  messageId: String,
  preComputedSubscription: Option[MessageSubscription] = None
)

class AmazonWriteAheadDAO(client: DynamoClient, walTable: String) extends WriteAheadDAO {

  implicit val defaultFormats: Formats = JSONFormats.formats

  private val maxWriteScheduleDelay = 6.seconds

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
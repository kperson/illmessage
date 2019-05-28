package com.github.kperson.delivery

import com.github.kperson.aws.dynamo.DynamoClient
import com.github.kperson.model.MessageSubscription
import com.github.kperson.serialization.JSONFormats
import com.github.kperson.wal.WALRecord
import com.github.kperson.util.Backoff

import java.nio.charset.StandardCharsets

import org.json4s.Formats
import org.json4s.jackson.Serialization.{read, write}

import scala.concurrent.Future


class AmazonDeliveryDAO(
  client: DynamoClient,
  deliveryTable: String,
  sequenceTable: String
 ) extends DeliveryDAO {

  import client.ec
  implicit val formats: Formats = JSONFormats.formats

  private val startingValue = Int.MinValue.toLong

  def queueMessages(subscriptions: List[MessageSubscription], record: WALRecord): Future[List[Delivery]] = {
    val deliveriesFut = Future.sequence(
      subscriptions.map { sub =>
        nextSequenceId(sub.id, record.message.groupId).map { dId =>
          //if there are no pending message for this group and subscription, allow it be sent immediately
          //otherwise, set it to pending
          val status = if (dId == startingValue + 1L) "inFlight" else "pending"
          Delivery(record.message, sub, dId, status, record.messageId)
        }
      }
    )
    deliveriesFut.flatMap { deliveries =>
      Backoff.runBackoffTask(7, 2, deliveries) { items =>
        client.batchPutItems(deliveryTable, items).map { _.unprocessedInserts }
      }.map { _ =>
        deliveries
      }
    }
  }

  def remove(delivery: Delivery): Future[Any] = {
    client.deleteItem[Delivery](
      deliveryTable,
      Map(
        "subscriptionId" -> delivery.subscription.id,
        "sequenceId" -> delivery.sequenceId
      )
    )
  }

  private def nextSequenceId(subscriptionId: String, groupId: String): Future[Long] = {
    val updateExpression = "SET subscriptionCt = if_not_exists(subscriptionCt, :startValue) + :inc"
    val key = Map(
      "subscriptionId" -> Map(
        "S" -> subscriptionId,
      ),
      "groupId" -> Map(
        "S" -> groupId,
      )
    )
    val expressionAttributeValues = Map(
      ":startValue" -> Map(
        "N" -> startingValue
      ),
      ":inc" -> Map(
        "N" -> "1"
      )
    )
    val body = Map(
      "TableName" -> sequenceTable,
      "Key" -> key,
      "UpdateExpression" -> updateExpression,
      "ExpressionAttributeValues" -> expressionAttributeValues,
      "ReturnValues" -> "ALL_NEW"
    )
    val payload = write(body).getBytes(StandardCharsets.UTF_8)
    client.request(payload, "DynamoDB_20120810.UpdateItem").run().map { res =>
      val m = read[Map[String, Any]](new String(res.body, StandardCharsets.UTF_8))
      val attributes = m("Attributes").asInstanceOf[Map[String, Any]]
      val subscriptionCt = attributes("subscriptionCt").asInstanceOf[Map[String, Any]]
      subscriptionCt("N").asInstanceOf[String].toLong
    }
  }

}
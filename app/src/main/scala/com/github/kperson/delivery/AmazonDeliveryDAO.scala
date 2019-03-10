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


class AmazonDeliveryDAO(client: DynamoClient, deliveryTable: String, sequenceTable: String) extends DeliveryDAO {

  import client.ec
  implicit val formats: Formats = JSONFormats.formats

  def queueMessages(subscriptions: List[MessageSubscription], record: WALRecord): Future[List[Delivery]] = {
    val groupings = subscriptions.grouped(25).toList
    val groupedJobs = groupings.map { queueGroupedMessages(_, record) }
    Future.sequence(groupedJobs).map { _.flatten.toList }
  }

  private def queueGroupedMessages(
    subscriptions: List[MessageSubscription],
    record: WALRecord
  ): Future[List[Delivery]] = {
    val deliveriesFut = Future.sequence(
      subscriptions.map { sub =>
        nextSequenceId(sub.id).map { dId =>
          Delivery(record.message, sub, dId)
        }
      }
    )
    deliveriesFut.flatMap { deliveries =>
      Backoff.runBackoffTask(5, deliveries) { items =>
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

  private def nextSequenceId(subscriptionId: String): Future[Long] = {
    val updateExpression = "SET subscriptionCt = if_not_exists(subscriptionCt, :startValue) + :inc"
    val key = Map(
      "subscriptionId" -> Map(
        "S" -> subscriptionId
      )
    )
    val expressionAttributeValues = Map(
      ":startValue" -> Map(
        "N" -> (Int.MinValue).toString
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
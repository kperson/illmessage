package com.github.kperson.delivery

import com.github.kperson.aws.dynamo.DynamoClient
import com.github.kperson.model.MessageSubscription
import com.github.kperson.serialization.JSONFormats
import com.github.kperson.wal.WALRecord
import com.github.kperson.util.Backoff
import com.github.kperson.aws.AWSError

import java.nio.charset.StandardCharsets

import org.json4s.Formats
import org.json4s.jackson.Serialization.{read, write}

import scala.concurrent.Future


class AmazonDeliveryDAO(
  client: DynamoClient,
  val deliveryTable: String,
  val sequenceTable: String
 ) extends DeliveryDAO {

  val inFlight = "inFlight"

  import client.ec
  implicit val formats: Formats = JSONFormats.formats

  private val startingValue = Int.MinValue.toLong

  def queueMessages(subscriptions: List[MessageSubscription], record: WALRecord): Future[List[Delivery]] = {
    val deliveriesFut = Future.sequence(
      subscriptions.map { sub =>
        nextSequenceId(sub.id, record.message.groupId).map { dId =>
          //if there are no pending message for this group and subscription, allow it be sent immediately
          //otherwise, set it to pending
          val status = if (dId == startingValue + 1L) inFlight else "pending"
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
        "S" -> subscriptionId
      ),
      "groupId" -> Map(
        "S" -> groupId
      )
    )
    val expressionAttributeValues = Map(
      ":startValue" -> Map(
        "N" -> startingValue.toString
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

  def ack(subscriptionId: String, groupId: String, sequenceId: Long): Future[Any] = {
    hasNewMessages(subscriptionId, groupId, sequenceId).flatMap { hasNew =>
      if(hasNew) {
        dequeue(subscriptionId)
      }
      else {
        Future.successful(true)
      }
    }
  }

  def bulkAck(requests: List[AckRequest]): Future[Any] = {
    val acks = requests.map { req => ack(req.subscriptionId, req.groupId, req.sequenceId) }
    Future.sequence(acks)
  }

  private def dequeue(subscriptionId: String): Future[Any] = {
    val keyConditionExpression = "subscriptionId = :subscriptionId"
    val filterExpression = "#status <> :inFlight"
    val expressionAttributeValues = Map(
      ":subscriptionId" -> subscriptionId,
      ":inFlight" -> inFlight
    )
    val expressionAttributeNames = Map(
      "#status" -> "status"
    )


    var delivery: Delivery = null

    Backoff.runBackoffTask(7, 2, List(true)) { _ =>
      val response = client.query[Delivery](
        deliveryTable,
        keyConditionExpression = keyConditionExpression,
        filterExpression = Some(filterExpression),
        expressionAttributeValues = expressionAttributeValues,
        expressionAttributeNames = expressionAttributeNames,
        scanIndexForward = true,
        limit = 1
      )
      response.map { paged =>
        if(paged.results.nonEmpty) {
          delivery = paged.results.head
          List.empty
        }
        else {
          List(true)
        }
      }
    }.flatMap { _ =>
      val inFlightDelivery = delivery.copy(status = inFlight)
      client.putItem(deliveryTable, inFlightDelivery)
    }
  }


  private def hasNewMessages(subscriptionId: String, groupId: String, sequenceId: Long): Future[Boolean] = {
    val key = Map(
      "subscriptionId" -> Map(
        "S" -> subscriptionId
      ),
      "groupId" -> Map(
        "S" -> groupId
      )
    )
    val expressionAttributeValues = Map(
      ":sequenceId" -> Map(
        "N" -> sequenceId.toString
      )
    )
    val body = Map(
      "Key" -> key,
      "TableName" -> sequenceTable,
      "ConditionExpression" -> "subscriptionCt = :sequenceId",
      "ExpressionAttributeValues" -> expressionAttributeValues,
      "ReturnValues" -> "NONE"
    )

    val payload = write(body).getBytes(StandardCharsets.UTF_8)
    client.request(payload, "DynamoDB_20120810.DeleteItem").run().map { _ =>
      false
    }.recover {
      case AWSError(response) if new String(response.body).contains("ConditionalCheckFailedException") => true
    }
  }

  def markDeadLetter(delivery: Delivery, errorMessage: String): Future[Any] = {
    client.putItem(deliveryTable, delivery.copy(status = "dead", error = Some(errorMessage)))
  }


}
package com.github.kperson.delivery

import com.github.kperson.model.{Message, MessageSubscription}
import com.github.kperson.serialization._
import com.github.kperson.test.dynamo.DynamoSupport
import com.github.kperson.test.spec.IllMessageSpec
import com.github.kperson.wal.WALRecord


class AmazonDeliveryDAOSpec extends IllMessageSpec with DynamoSupport with TestSupport {


  "AmazonDeliveryDAO" should "queue messages" in withDynamo { (_, _, dynamoClient) =>
    val client = new AmazonDeliveryDAO(dynamoClient, "mailbox", "subMessageSequence")
    import dynamoClient.ec
    val job = for {
      deliveries <- client.queueMessages(List(subscription), record)
      fetch <- dynamoClient.getItem[Delivery](
        "mailbox",
        Map("subscriptionId" -> subscription.id, "sequenceId" -> deliveries.head.sequenceId)
      )
    } yield {
      fetch
    }

    val expectedValue = Some(Delivery(message, subscription, Int.MinValue + 1, "inFlight", record.messageId))
    whenReady(job, secondsTimeOut(3)) { rs =>
      rs should be (expectedValue)
    }
  }

  it should "remove messages" in withDynamo { (_, _, dynamoClient) =>
    val client = new AmazonDeliveryDAO(dynamoClient, "mailbox", "subMessageSequence")
    import dynamoClient.ec
    val job = for {
      deliveries <- client.queueMessages(List(subscription), record)
      delivery <- client.remove(deliveries.head).map { _  => deliveries.head }
      fetch <- dynamoClient.getItem[Delivery](
        "mailbox",
        Map("subscriptionId" -> subscription.id, "sequenceId" -> delivery.sequenceId)
      )
    } yield fetch

    whenReady(job, secondsTimeOut(3)) { rs =>
      rs.isEmpty should be (true)
    }
  }

  it should "mark dead letters" in withDynamo { (_, _, dynamoClient) =>
    val client = new AmazonDeliveryDAO(dynamoClient, "mailbox", "subMessageSequence")
    import dynamoClient.ec
    val delivery = Delivery(message, subscription, 3L, "inFlight", record.messageId)

    val job = dynamoClient.putItem(client.deliveryTable, delivery).flatMap { _ =>
      client.markDeadLetter(delivery, new RuntimeException("my error"))
    }.flatMap { _ =>
      dynamoClient.getItem[Delivery](
        client.deliveryTable,
        Map("subscriptionId" -> delivery.subscription.id, "sequenceId" -> delivery.sequenceId)
      )
    }.map { _.get }

    whenReady(job, secondsTimeOut(3)) { rs =>
      rs.status should be ("dead")
    }
  }

  it should "ack and delete sequences if no more messages are present" in withDynamo { (_, _, dynamoClient) =>
    val client = new AmazonDeliveryDAO(dynamoClient, "mailbox", "subMessageSequence")
    import dynamoClient.ec
    val delivery = Delivery(message, subscription, 3L, "inFlight", record.messageId)
    val add = dynamoClient.putItem(
      "subMessageSequence",
      Map("subscriptionId" -> delivery.subscription.id, "groupId" -> delivery.message.groupId, "subscriptionCt" -> 3L)
    )

    val flow = add.flatMap { _ =>
      client.ack(delivery.subscription.id, delivery.message.groupId, 3L)
    }.flatMap { _ =>
      dynamoClient.getItem[Map[String, Any]]("subMessageSequence", Map("subscriptionId" -> delivery.subscription.id, "groupId" -> delivery.message.groupId))
    }
    whenReady(flow, secondsTimeOut(5)) { rs =>
      rs should be (None)
    }
  }

  it should "ack and dequeue if more messages are present" in withDynamo { (_, _, dynamoClient) =>
    val client = new AmazonDeliveryDAO(dynamoClient, "mailbox", "subMessageSequence")
    import dynamoClient.ec

    val inFlightDelivery = Delivery(message, subscription, 3L, "inFlight", record.messageId)
    val nextDelivery = Delivery(message, subscription, 4L, "pending", record.messageId)

    val add = dynamoClient.putItem(
      "subMessageSequence",
      Map(
        "subscriptionId" -> nextDelivery.subscription.id,
        "groupId" -> nextDelivery.message.groupId,
        "subscriptionCt" -> nextDelivery.sequenceId
      )
    )

    val flow = add.flatMap { _ =>
      dynamoClient.putItem("mailbox", nextDelivery)

    }.flatMap { _ =>
      client.ack(inFlightDelivery.subscription.id, inFlightDelivery.message.groupId, 3L)
    }.flatMap { _ =>
      dynamoClient.getItem[Delivery](
        "mailbox",
        Map("subscriptionId" -> nextDelivery.subscription.id, "sequenceId" -> nextDelivery.sequenceId)
      )
    }.map { d =>
      d.map { _.status }
    }

    whenReady(flow, secondsTimeOut(5)) { rs =>
      rs should be (Some("inFlight"))
    }
  }

}

trait TestSupport {
  val message = Message(
    "my-r-key-1",
    "hello world",
    "exchange-one",
    "group-one"
  )

  val subscription = MessageSubscription(
    "e1",
    "com.*.hello",
    "q1",
    "782056314912",
    "active"
  )

  val record = WALRecord(
    message,
    "id-one"
  )
}

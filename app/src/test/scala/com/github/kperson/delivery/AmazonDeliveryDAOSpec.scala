package com.github.kperson.delivery

import com.github.kperson.model.{Message, MessageSubscription}
import com.github.kperson.serialization.JSONFormats
import com.github.kperson.test.dynamo.DynamoSupport
import com.github.kperson.test.spec.IllMessageSpec
import com.github.kperson.wal.WALRecord

import org.json4s.Formats


class AmazonDeliveryDAOSpec extends IllMessageSpec with DynamoSupport with TestSupport {

  implicit val formats: Formats = JSONFormats.formats

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

  it should "ack" in withDynamo { (_, _, dynamoClient) =>
    val client = new AmazonDeliveryDAO(dynamoClient, "mailbox", "subMessageSequence")
    whenReady(client.ack("one", "two", 4), secondsTimeOut(5)) { x =>
      print(x)
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

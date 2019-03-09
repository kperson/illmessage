package com.github.kperson.delivery

import com.github.kperson.model.{Message, MessageSubscription}
import com.github.kperson.serialization.JSONFormats
import com.github.kperson.test.dynamo.DynamoSupport
import com.github.kperson.test.spec.IllMessageSpec
import com.github.kperson.wal.WALRecord


class AmazonDeliveryDAOSpec extends IllMessageSpec with DynamoSupport with TestSupport {

  implicit val formats = JSONFormats.formats

  "AmazonDeliveryClient" should "queue messages" in withDynamo { (_, _, dynamoClient) =>
    val client = new AmazonDeliveryDAO(dynamoClient, "mailbox")
    import dynamoClient.ec
    val job = for {
      deliveries <- client.queueMessages(List(subscription), record)
      fetch <- dynamoClient.getItem[Delivery](
        "mailbox",
        Map("subscriptionId" -> subscription.id, "createdAt" -> deliveries.head.createdAt.getTime)
      )
    } yield fetch

    whenReady(job, secondsTimeOut(3)) { rs =>
      rs.isDefined should be (true)
    }
  }

  it should "remove messages" in withDynamo { (_, _, dynamoClient) =>
    val client = new AmazonDeliveryDAO(dynamoClient, "mailbox")
    import dynamoClient.ec
    val job = for {
      deliveries <- client.queueMessages(List(subscription), record)
      delivery <- client.remove(deliveries.head).map { _  => deliveries.head }
      fetch <- dynamoClient.getItem[Delivery](
        "mailbox",
        Map("subscriptionId" -> subscription.id, "createdAt" -> delivery.createdAt.getTime)
      )
    } yield fetch

    whenReady(job, secondsTimeOut(3)) { rs =>
      rs.isEmpty should be (true)
    }
  }

}

trait TestSupport {
  val message = Message(
    "my-r-key-1",
    "hello world",
    "exchange-one",
    None,
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

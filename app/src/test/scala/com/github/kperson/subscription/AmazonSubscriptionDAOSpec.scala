package com.github.kperson.subscription

import com.github.kperson.delivery.Delivery
import com.github.kperson.model.{Message, MessageSubscription}
import com.github.kperson.test.dynamo.DynamoSupport
import com.github.kperson.test.spec.IllMessageSpec
import com.github.kperson.wal.WALRecord
import com.github.kperson.serialization._


import scala.concurrent.ExecutionContext.Implicits.global


class AmazonSubscriptionDAOSpec extends IllMessageSpec with DynamoSupport with TestSupport {

  "SubscriptionDAO" should "register subscriptions" in withDynamo { (_, _, client) =>
    val dao = new AmazonSubscriptionDAO(client, "subscription", "mailbox")

    val writeFetch = for {
      sub <- dao.save(subscription)
      rs <- dao.fetchSubscriptions(sub.exchange, subscription.bindingKey)
    }  yield rs.head

    whenReady(writeFetch, secondsTimeOut(3)) { rs =>
      rs should be (subscription)
    }

  }

  it should "remove subscriptions" in withDynamo { (_, _, client) =>
    val dao = new AmazonSubscriptionDAO(client, "subscription", "mailbox")
    val writeDeleteFetch = for {
      sub <- dao.save(subscription)
      rs <- dao.delete(sub.exchange, subscription.id).flatMap { _ =>
        dao.fetchSubscriptions(sub.exchange, subscription.bindingKey)
      }
    } yield rs.headOption

    whenReady(writeDeleteFetch, secondsTimeOut(3)) { rs =>
      rs should be (None)
    }
  }

  it should "remove all deliveries" in withDynamo { (_, _, client) =>
    val dao = new AmazonSubscriptionDAO(client, "subscription", "mailbox")

    val fetch = client.putItem("mailbox", delivery).flatMap { _ =>
      dao.save(subscription)
    }.flatMap { _ =>
      dao.delete(subscription.exchange, subscription.id)
    }.flatMap { _ =>
      client.getItem[Delivery]("mailbox", Map("subscriptionId" -> subscription.id, "sequenceId" -> delivery.sequenceId))
    }
    whenReady(fetch, secondsTimeOut(3)) { rs =>
      rs should be (None)
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

  val delivery = Delivery(message, subscription, 3L, "inFlight", record.messageId, None)


}

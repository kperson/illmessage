package com.github.kperson.subscription

import com.github.kperson.model.MessageSubscription
import com.github.kperson.test.dynamo.DynamoSupport
import com.github.kperson.test.spec.IllMessageSpec

import scala.concurrent.ExecutionContext.Implicits.global


class SubscriptionDAOSpec extends IllMessageSpec with DynamoSupport {

  val subscription = MessageSubscription(
    "e1",
    "com.*.hello",
    "q1",
    "782056314912",
    "active"
  )

  "SubscriptionDAO" should "register subscriptions" in withDynamo { (_, _, client) =>
    val dao = new AmazonSubscriptionDAO(client, "subscription")

    val writeFetch = for {
      sub <- dao.save(subscription)
      rs <- dao.fetchSubscriptions(sub.exchange, subscription.bindingKey)
    }  yield rs.head

    whenReady(writeFetch, secondsTimeOut(3)) { rs =>
      rs should be (subscription)
    }

  }

  it should "remove subscriptions" in withDynamo { (_, _, client) =>
    val dao = new AmazonSubscriptionDAO(client, "subscription")
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

}

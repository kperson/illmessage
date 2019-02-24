package com.github.kperson.wal

import com.github.kperson.model.{Message, MessageSubscription}
import com.github.kperson.test.dynamo.DynamoSupport
import com.github.kperson.test.spec.IllMessageSpec

import scala.concurrent.ExecutionContext.Implicits.global


class AmazonWriteAheadDAOSpec extends IllMessageSpec with DynamoSupport {

  val message = Message(
    "my-r-key-1",
    "hello world",
    "exchange-one",
    None,
    "group-one"
  )

  "AmazonWriteAheadDAO" should "write messages" in withDynamo { (_, _, client) =>
    val dao = new AmazonWriteAheadDAO(client, "wal")
    val writeFetch = for {
       ids <- dao.write(List(message))
       r <- dao.fetchWALRecord(ids.head, message.partitionKey)
    } yield r.map { _.message }


    whenReady(writeFetch, secondsTimeOut(3)) { results =>
      results should be (Some(message))
    }
  }

  it should "write messages with subscriptions" in withDynamo { (_, _, client) =>
    val dao = new AmazonWriteAheadDAO(client, "wal")
    val messageSubscription = Some(MessageSubscription(
      "exchange-one",
      "my-r-key-1",
      "q1", "my-aws-account",
      "active"
    ))
    val payload = (message, messageSubscription)

    val writeFetch = for {
      ids <- dao.writeWithSubscription(List(payload))
      r <- dao.fetchWALRecord(ids.head, message.partitionKey)
    } yield r.map { rs => (rs.message, rs.preComputedSubscription) }


    whenReady(writeFetch, secondsTimeOut(3)) { results =>
      results.get should be (payload)
    }
  }

  it should "remove records" in withDynamo { (_, _, client) =>
    val dao = new AmazonWriteAheadDAO(client, "wal")
    val writeDeleteFetch = for {
      ids <- dao.write(List(message))
      r <- dao.remove(ids.head, message.partitionKey).flatMap { _ =>
        dao.fetchWALRecord(ids.head, message.partitionKey)
      }
    } yield r

    whenReady(writeDeleteFetch, secondsTimeOut(2)) { results =>
      results should be (None)
    }
  }

}

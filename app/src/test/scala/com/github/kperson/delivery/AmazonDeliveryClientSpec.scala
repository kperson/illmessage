package com.github.kperson.delivery

import com.github.kperson.model.{Message, MessageSubscription}
import com.github.kperson.test.dynamo.DynamoSupport
import com.github.kperson.test.spec.IllMessageSpec
import com.github.kperson.wal.WALRecord

import scala.concurrent.ExecutionContext.Implicits.global


class AmazonDeliveryClientSpec extends IllMessageSpec with DynamoSupport with TestSupport {

  "AmazonDeliveryClient" should "queue messages" in withDynamo { (_, _, dynamoClient) =>
    val client = new AmazonDeliveryClient(dynamoClient, "mailbox")
    val job = client.queueMessages(List(subscription), record)
    whenReady(job, secondsTimeOut(3)) { rs =>
      rs should be (true)
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

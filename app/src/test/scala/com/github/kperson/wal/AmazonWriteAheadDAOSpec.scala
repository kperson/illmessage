package com.github.kperson.wal

import com.github.kperson.model.Message
import com.github.kperson.test.dynamo.DynamoSupport
import com.github.kperson.test.spec.IllMessageSpec

import scala.concurrent.ExecutionContext.Implicits.global


class AmazonWriteAheadDAOSpec extends IllMessageSpec with DynamoSupport {

  val message = Message(
    "my-r-key-1",
    "hello world",
    "exchange-one",
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

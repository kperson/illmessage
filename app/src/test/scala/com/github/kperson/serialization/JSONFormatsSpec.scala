package com.github.kperson.serialization

import com.github.kperson.model.{Message, MessageSubscription}
import com.github.kperson.wal.WALRecord

import org.json4s.Formats
import org.scalatest.{FlatSpec, Matchers}
import org.json4s.jackson.Serialization._


class JSONFormatsSpec extends FlatSpec with Matchers {

  implicit val formats: Formats = JSONFormats.formats

  val message = Message("abc.321", "b2", "e1", groupId = "g1")
  val subscription = MessageSubscription("e1", "b1", "q1", "a1")

  "JSONFormats" should "serialize wal records" in {
    val recordOne = WALRecord(message, "ABC")
    val expectedOne = read[WALRecord](write(recordOne))
    expectedOne should be (recordOne)
    read[Map[String, Any]](write(recordOne)).get("partitionKey").isDefined should be (true)

    val recordTwo = WALRecord(message, "ABC", preComputedSubscription = Some(subscription))
    val expectedTwo = read[WALRecord](write(recordTwo))
    expectedTwo should be (recordTwo)
  }


}

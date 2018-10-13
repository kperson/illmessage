package com.github.kperson.serialization

import com.github.kperson.model.{Message, MessageSubscription}
import com.github.kperson.wal.WALRecord

import org.json4s.{CustomSerializer, Extraction, Formats}
import org.json4s.JsonAST._


class WALRecordSerializer extends CustomSerializer[WALRecord](format => (
  {
    case json: JObject =>
      implicit val formats: Formats = format
      val messageId = (json \ "messageId").extract[String]
      val message = (json \ "message").extract[Message]
      val preComputedSubscription = (json \ "preComputedSubscription").extract[Option[MessageSubscription]]
      WALRecord(message, messageId, preComputedSubscription)
  },
  {
    case record: WALRecord =>
      JObject(
        JField("messageId", JString(record.messageId)) ::
        JField("message", Extraction.decompose(record.message)(format)) ::
        JField("partitionKey", JString(record.message.partitionKey)) ::
        JField("preComputedSubscription", Extraction.decompose(record.preComputedSubscription)(format)) ::
        Nil
      )
  }
))
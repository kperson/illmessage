package com.github.kperson.serialization

import com.github.kperson.deadletter.DeadLetterMessage
import com.github.kperson.model.{Message, MessageSubscription}
import org.json4s.{CustomSerializer, Extraction}
import org.json4s.JsonAST._


class DeadLetterMessageSerializer extends CustomSerializer[DeadLetterMessage](format => (
  {
    case json: JObject =>
      implicit val formats = format
      val messageId = (json \ "messageId").extract[String]
      val insertedAt = (json \ "insertedAt").extract[Long]
      val ttl = (json \ "ttl").extract[Long]
      val message = (json \ "message").extract[Message]
      val reason = (json \ "reason").extract[String]
      val subscription = (json \ "subscription").extract[MessageSubscription]
      DeadLetterMessage(subscription, messageId, message, insertedAt, ttl, reason)
  },
  {
    case dlm: DeadLetterMessage =>
      JObject(
        JField("messageId", JString(dlm.messageId)) ::
        JField("insertedAt", JLong(dlm.insertedAt)) ::
        JField("ttl", JLong(dlm.ttl)) ::
        JField("subscriptionId", JString(dlm.subscription.id)) ::
        JField("message", Extraction.decompose(dlm.message)(format)) ::
        JField("subscription", Extraction.decompose(dlm.subscription)(format)) ::
        JField("reason", JString(dlm.reason)) ::
        Nil
      )
  }
))
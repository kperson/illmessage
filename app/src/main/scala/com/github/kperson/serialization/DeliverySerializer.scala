package com.github.kperson.serialization

import com.github.kperson.delivery.Delivery
import com.github.kperson.model.{Message, MessageSubscription}
import org.json4s.JsonAST._
import org.json4s.{CustomSerializer, Extraction, Formats}

class DeliverySerializer extends CustomSerializer[Delivery](format => (
  {
    case json: JObject =>
      implicit val formats: Formats = format
      val message = (json \ "message").extract[Message]
      val subscription = (json \ "subscription").extract[MessageSubscription]
      val sequenceId = (json \ "sequenceId").extract[Long]
      val status = (json \ "status").extract[String]
      val messageId = (json \ "messageId").extract[String]
      val error = (json \ "error").extract[Option[String]]
      Delivery(message, subscription, sequenceId, status, messageId, error)
  },
  {
    case delivery: Delivery =>
      val f = List(
        JField("message", Extraction.decompose(delivery.message)(format)),
        JField("subscription", Extraction.decompose(delivery.subscription)(format)),
        JField("subscriptionStatus", JString(delivery.subscription.status)),
        JField("subscriptionId", JString(delivery.subscription.id)),
        JField("sequenceId", JLong(delivery.sequenceId)),
        JField("status", JString(delivery.status)),
        JField("messageId", JString(delivery.messageId))
      )
      JObject(delivery.error match {
        case Some(e) =>  f ++ List(JField("error", JString(e)))
        case _ => f
      })
  }
))
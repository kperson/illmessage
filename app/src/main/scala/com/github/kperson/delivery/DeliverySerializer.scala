package com.github.kperson.delivery

import java.util.Date

import com.github.kperson.model.{Message, MessageSubscription}
import org.json4s.{CustomSerializer, Extraction, Formats}
import org.json4s.JsonAST._

class DeliverySerializer extends CustomSerializer[Delivery](format => (
  {
    case json: JObject =>
      implicit val formats: Formats = format
      val message = (json \ "message").extract[Message]
      val subscription = (json \ "subscription").extract[MessageSubscription]
      val createdAt = (json \ "createdAt").extract[Long]

      Delivery(message, subscription, new Date(createdAt))
  },
  {
    case delivery: Delivery =>
      JObject(
        JField("message", Extraction.decompose(delivery.message)(format)) ::
        JField("subscription", Extraction.decompose(delivery.subscription)(format)) ::
        JField("status", JString(delivery.subscription.status)) ::
        JField("subscriptionId", JString(delivery.subscription.id)) ::
        JField("createdAt", JLong(delivery.createdAt.getTime)) ::
        Nil
      )
  }
))
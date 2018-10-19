package com.github.kperson.serialization

import com.github.kperson.model.MessageSubscription
import org.json4s.{CustomSerializer, Formats}
import org.json4s.JsonAST._


class MessageSubscriptionSerializer extends CustomSerializer[MessageSubscription](format => (
  {
    case json: JObject =>
      implicit val formats: Formats = format
      val exchange = (json \ "exchange").extract[String]
      val bindingKey = (json \ "bindingKey").extract[String]
      val queue = (json \ "queue").extract[String]
      val accountId = (json \ "accountId").extract[String]
      MessageSubscription(exchange, bindingKey, queue, accountId)
  },
  {
    case subscription: MessageSubscription =>
      JObject(
        JField("exchange", JString(subscription.exchange)) ::
        JField("bindingKey", JString(subscription.bindingKey)) ::
        JField("queue", JString(subscription.queue)) ::
        JField("accountId", JString(subscription.accountId)) ::
        JField("subscriptionId", JString(subscription.id)) ::
        JField("bindingKeyComponents", JArray(subscription.bindingKeyComponents.map { JString(_) })) ::
        JField("bindingKeyComponentsSize", JInt(subscription.bindingKeyComponents.length.toLong)) ::
        Nil
      )
  }
))
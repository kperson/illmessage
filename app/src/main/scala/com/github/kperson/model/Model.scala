package com.github.kperson.model

case class Message(
 routingKey: String,
 body: String,
 exchange: String
)

case class Subscription(
  exchange: String,
  bindingKey: String,
  queue: String,
  accountId: String
) {

  def id: String = {
    val text = s"exchange:$exchange:bindingKey:$bindingKey:queue:$queue:accountId:$accountId"
    java.security.MessageDigest.getInstance("MD5")
      .digest(text.getBytes())
      .map(0xFF & _)
      .map { "%02x".format(_) }
      .foldLeft(""){_ + _}
  }

}

case class DeadLetterMessage(
  id: String,
  subscription: Subscription,
  message: Message,
  reason: String,
  ttl: Long
)
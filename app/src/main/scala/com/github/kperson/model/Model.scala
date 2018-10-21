package com.github.kperson.model

case class Message(
  routingKey: String,
  body: String,
  exchange: String,
  delayInSeconds: Option[Int] = None,
  groupId: String
) {
  require(body.length <= 256 * 1024, "message body must be less than or equal to 256 KB")

  def partitionKey = {
    val text = s"groupId:$groupId:exchange:$exchange"
    java.security.MessageDigest.getInstance("MD5")
      .digest(text.getBytes())
      .map(0xFF & _)
      .map { "%02x".format(_) }
      .foldLeft(""){_ + _}
  }

}

case class MessageSubscription(
  exchange: String,
  bindingKey: String,
  queue: String,
  accountId: String,
  status: String = "active"
) {

  require(List("active", "transitioning", "inactive").contains(status), "status must be active, transitioning, inactive")

  def id: String = {
    val text = s"exchange:$exchange:bindingKey:$bindingKey:queue:$queue:accountId:$accountId"
    java.security.MessageDigest.getInstance("MD5")
      .digest(text.getBytes())
      .map(0xFF & _)
      .map { "%02x".format(_) }
      .foldLeft(""){_ + _}
  }

  def bindingKeyComponents: List[String] = bindingKey.split("\\.").toList

}
package com.github.kperson.model

import com.github.kperson.util.MD5

case class Message(
  routingKey: String,
  body: String,
  exchange: String,
  delayInSeconds: Option[Int] = None,
  groupId: String
) {
  require(body.length <= 256 * 1024, "message body must be less than or equal to 256 KB")

  def partitionKey: String = {
    MD5.hash(s"groupId:$groupId:exchange:$exchange")
  }

}

case class MessageSubscription(
  exchange: String,
  bindingKey: String,
  queue: String,
  accountId: String,
  status: String = "active"
) {

  val allowedStatuses = List("active", "transitioning", "locked", "inactive")
  require(allowedStatuses.contains(status), s"status must be ${allowedStatuses.mkString(", ")}")

  def id: String = {
    MD5.hash(s"exchange:$exchange:bindingKey:$bindingKey:queue:$queue:accountId:$accountId")
  }

  def bindingKeyComponents: List[String] = bindingKey.split("\\.").toList

}
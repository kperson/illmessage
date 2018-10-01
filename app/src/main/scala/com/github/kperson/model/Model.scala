package com.github.kperson.model

case class Message(
  routingKey: String,
  body: String,
  exchange: String,
  delayInSeconds: Option[Int] = None
) {
  require(body.length <= 256 * 1024, "message body must be less than 256 KB")
}

case class MessageSubscription(
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
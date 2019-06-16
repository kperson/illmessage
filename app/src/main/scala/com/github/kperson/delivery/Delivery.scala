package com.github.kperson.delivery


import com.github.kperson.model.{Message, MessageSubscription}


case class DeliveryError(message: String, stackTrace: List[String])
case class Delivery(message: Message, subscription: MessageSubscription, sequenceId: Long, status: String, messageId: String, error: Option[DeliveryError] = None)
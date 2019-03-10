package com.github.kperson.delivery


import com.github.kperson.model.{Message, MessageSubscription}

case class Delivery(message: Message, subscription: MessageSubscription, sequenceId: Long)
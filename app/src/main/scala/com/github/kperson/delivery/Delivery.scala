package com.github.kperson.delivery

import java.util.Date

import com.github.kperson.model.{Message, MessageSubscription}

case class Delivery(message: Message, subscription: MessageSubscription, createdAt: Date)

package com.github.kperson.redeliver

import com.github.kperson.deadletter.DeadLetterQueueDAO
import com.github.kperson.model.MessageSubscription

import scala.concurrent.Future

object Redelivery {

  def unapply(args: Array[String]): Option[MessageSubscription] = {
    args.toList match {
      case head :: tail if head == "redeliver" && tail.length == 4 =>
        Some(MessageSubscription(
          tail(0),
          tail(1),
          tail(2),
          tail(3)
        ))
      case _ => None
    }
  }

  def apply(
    subscription: MessageSubscription,
    deadLetterQueueDAO: DeadLetterQueueDAO
  ): Future[Any] = {
    deadLetterQueueDAO.loadToWAL(subscription)
  }

}
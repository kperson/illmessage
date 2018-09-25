package com.github.kperson.aws.sqs

import org.reactivestreams.{Publisher, Subscriber}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.stm.Ref


case class BackOffStrategy(initialTimeout: FiniteDuration, maxTimeout: FiniteDuration)

class SQSPublisher(
  client: SQSQueueClient,
  queueName: String,
  autoDelete: Boolean = false,
  backOffStrategy: Option[BackOffStrategy] = None
) extends Publisher[SNSMessage[String]]  {

  val subscribers = Ref(Set[Subscriber[_ >: SNSMessage[String]]]())

  def subscribe(s: Subscriber[_ >: SNSMessage[String]]) {
    subscribers.single.getAndTransform(_ + s) match {
      case ss if ss.contains(s) =>
        throw new IllegalStateException(s"Rule 1.10: Subscriber=$s is already subscribed to this publisher.")
      case _ =>
        val subscription = new SQSSubscription(s, client, queueName, autoDelete, backOffStrategy)
        s.onSubscribe(subscription)
    }
  }

}

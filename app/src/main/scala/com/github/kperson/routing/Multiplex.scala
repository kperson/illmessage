package com.github.kperson.routing

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Sink, Source}

import com.github.kperson.model.Message
import com.github.kperson.routing.Multiplex._

import org.reactivestreams.{Publisher, Subscriber, Subscription}

import com.github.kperson.model.MessageSubscription

import scala.concurrent.stm.{atomic, Ref}


case class MessagePayload[C](message: Message, subscription: MessageSubscription, messageId: MessageId, context: C)
case class MessageSubscriptions[C](message: Message, subscriptions: List[MessageSubscription], messageId: MessageId, context: C)


trait ACKCallback[C] {

  def ack(messageId: String, context: C)

}

object Multiplex {

  type MessageId = String
  type SubscriptionId = String
  type MessageCount = Long
  type IsPendingDelivery = Boolean

  def flow[C](
    ackCallback: ACKCallback[C]
 ): (Flow[MessageSubscriptions[C], MessagePayload[C], NotUsed], (String, String) => Unit) = {
    val multi = new Multiplex[C](ackCallback)
    (Flow.fromSinkAndSource(Sink.fromSubscriber(multi), Source.fromPublisher(multi)), multi.onMessageSent)
  }

}


class Multiplex[C](ackCallback: ACKCallback[C]) extends Subscriber[MessageSubscriptions[C]] with Publisher[MessagePayload[C]] {

  //PUBLISHER
  private var downStreamSubscription: Option[MultiplexSubscription[C]] = None
  private var downStreamSubscriber: Option[Subscriber[_ >: MessagePayload[C]]] = None


  def onMessageSent(subscriptionId: SubscriptionId, messageId: MessageId) {
    downStreamSubscription.foreach { _.onDownStreamMessageComplete(subscriptionId, messageId) }
  }

  def subscribe(s: Subscriber[_ >: MessagePayload[C]]) {
    downStreamSubscriber = Some(s)
    (downStreamSubscriber, upstreamSubscription) match {
      case (Some(dSubscriber), Some(uSubscription)) =>
        val subscription = new MultiplexSubscription[C](
          uSubscription,
          dSubscriber,
          ackCallback
        )

        downStreamSubscription = Some(subscription)
        dSubscriber.onSubscribe(subscription)
      case _ =>
    }
  }

  //SUBSCRIBER
  private var upstreamSubscription: Option[Subscription] = None

  def onSubscribe(s: Subscription) {
    upstreamSubscription = Some(s)
    (downStreamSubscriber, upstreamSubscription) match {
      case (Some(dSubscriber), Some(uSubscription)) =>
        val subscription = new MultiplexSubscription[C](
          uSubscription,
          dSubscriber,
          ackCallback
        )
        downStreamSubscription = Some(subscription)
        dSubscriber.onSubscribe(subscription)
      case _ =>
    }
  }

  def onNext(t: MessageSubscriptions[C]) {
    downStreamSubscription.foreach { _.onUpStreamMessageReceived(t) }
  }

  def onComplete() {
    downStreamSubscription.foreach { _.onUpstreamComplete() }
  }

  def onError(t: Throwable) {
    downStreamSubscriber.foreach { _.onError(t) }
  }

}

class MultiplexSubscription[C](
  upstreamSubscription: Subscription,
  downstreamSubscriber: Subscriber[_ >: MessagePayload[C]],
  ackCallback: ACKCallback[C],
) extends Subscription {

  val downstreamDemand = Ref(0L)
  val queuedForSendCount = Ref(0L)

  val messageMap = Ref(Map[String, (Message, MessageCount, C)]())
  val subscriptionMap = Ref(Map[SubscriptionId, (MessageSubscription, List[(MessageId, IsPendingDelivery)])]())
  val pendingPayloads = Ref(List[MessagePayload[C]]())
  val isRunning = Ref(false)
  private var isOpen = true
  private var upstreamComplete = false



  def request(n: Long) {
    upstreamSubscription.request(n)
    downstreamDemand.single.transformAndGet(_ + n)
    deliveryRequested()
  }

  def cancel() {
    isOpen = false
  }

  private def deliveryRequested() {
    val shouldDeliver = atomic { implicit tx =>
      if (downstreamDemand() > 0 && pendingPayloads().nonEmpty) {
        isRunning.transformAndExtract { ir => (true, !ir) }
      }
      else {
        false
      }
    }
    if (shouldDeliver) {
      deliver()
    }
  }

  private def deliver() {
    val items = atomic { implicit tx =>
      val hasDemand = downstreamDemand() > 0
      val isR = isRunning() && hasDemand
      val pending = pendingPayloads()
      val items = if(isR) {
        val dequeueAmount = math.min(downstreamDemand(), pending.length)
        pendingPayloads.set(pending.drop(dequeueAmount.toInt))
        queuedForSendCount.transform { _ + dequeueAmount }
        pending.take(dequeueAmount.toInt)
      }
      else {
        List.empty
      }
      items
    }
    if(items.nonEmpty) {
      items.foreach { i =>
        if(isOpen) {
          downstreamSubscriber.onNext(i)
        }
      }
      val (shouldDeliver) = atomic { implicit tx =>
        val newDemand = downstreamDemand.transformAndGet { _ - items.length }
        isRunning.transformAndGet { _ => newDemand > 0 && pendingPayloads().nonEmpty }
      }
      if(shouldDeliver) {
        deliver()
      }
      else {
        checkCompletion()
      }

    }
    else {
      atomic { implicit tx =>
        isRunning.set(false)
      }
      checkCompletion()
    }
  }

  def onUpstreamComplete() {
    upstreamComplete = true
    checkCompletion()
  }

  def checkCompletion() {
    if(upstreamComplete) {
      val isComplete = atomic { implicit tx =>
        pendingPayloads().isEmpty && messageMap().isEmpty
      }
      if(isComplete) {
        downstreamSubscriber.onComplete()
      }
    }
  }

  def onUpStreamMessageReceived(groupedPayload: MessageSubscriptions[C]) {
    atomic { implicit tx =>
      groupedPayload.subscriptions.foreach { s =>
        val mMap = messageMap()
        mMap.get(groupedPayload.messageId) match {
          case Some((m, ct, context)) =>
            messageMap.set(mMap + (groupedPayload.messageId -> (m, ct + 1L, context)))
          case _ =>
            messageMap.set(mMap + (groupedPayload.messageId -> (groupedPayload.message, 1L, groupedPayload.context)))
        }
        val (sub, subMessages) = subscriptionMap().getOrElse(s.id, (s, List.empty))
        val shouldAddToPending = subMessages.isEmpty
        if (shouldAddToPending) {
          val payload = MessagePayload(groupedPayload.message, s, groupedPayload.messageId, groupedPayload.context)
          pendingPayloads.transform { _ ++ List(payload) }
        }
        subscriptionMap.transform {
          _ + (sub.id -> (sub, subMessages ++ List((groupedPayload.messageId, shouldAddToPending))))
        }
      }
    }
    deliveryRequested()
  }

  def onDownStreamMessageComplete(subscriptionId: SubscriptionId, messageId: MessageId) {
    val (ackRequired, context) = atomic { implicit tx =>
      queuedForSendCount.transform { _ - 1L }
      val mMap = messageMap()
      val (sentMessage, remainingCount, completedMessageContext) = mMap(messageId)
      val ackRequired = if(remainingCount == 1L) {
        //if this is the last item, remove the map
         messageMap.set(mMap - messageId)
        true
      }
      else {
        //drop the reference count by 1
        messageMap.set(mMap + (messageId -> (sentMessage, remainingCount - 1L, completedMessageContext)))
        false
      }

      val sMap = subscriptionMap()
      val (sub, allMessages) = sMap(subscriptionId)
      val newMessages = allMessages.filter { case (mId, _) => mId != messageId }
      if(newMessages.isEmpty) {
        //if there are no more message in this subscription, just delete the reference
        subscriptionMap.set(sMap - subscriptionId)
      }
      else {
        //get the next, message, updates its status to pending, update the subscription list
        val (nextMessageId, _) = newMessages.head
        val newMessageList = List((nextMessageId, true)) ++ newMessages.drop(1)
        subscriptionMap.set(sMap + (subscriptionId -> (sub, newMessageList)))
        val (nextMessage, _, nextMessageContext) = mMap.apply(nextMessageId)
        pendingPayloads.transform { _ ++ List(MessagePayload(nextMessage, sub, nextMessageId, nextMessageContext)) }
      }
      (ackRequired, completedMessageContext)
    }
    if(ackRequired) {
      ackCallback.ack(messageId, context)
    }
    checkCompletion()
    deliveryRequested()
  }


}
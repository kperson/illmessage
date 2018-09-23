package com.github.kperson.routing

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Sink, Source}

import com.github.kperson.model.Message
import com.github.kperson.routing.Multiplex._

import org.reactivestreams.{Publisher, Subscriber, Subscription}

import com.github.kperson.model.{Subscription => MessageSubscription}

import scala.concurrent.stm.{atomic, Ref}


case class MessagePayload[C](message: Message, subscription: MessageSubscription, messageId: MessageId, context: C)

trait ACKCallback[C] {

  def ack(messageId: String, context: C)

}

object Multiplex {

  type MessageId = String
  type SubscriptionId = String
  type MessageCount = Long
  type IsPendingDelivery = Boolean

  def source[C](
    queuedForSendLimit: Long,
    requestIncrement: Long,
    ackCallback: ACKCallback[C]
 ): Flow[MessagePayload[C], MessagePayload[C], NotUsed] = {
    val multi = new Multiplex[C](queuedForSendLimit, requestIncrement, ackCallback)
    Flow.fromSinkAndSourceCoupled(Sink.fromSubscriber(multi), Source.fromPublisher(multi))
  }

}


class Multiplex[C](
  queuedForSendLimit: Long,
  requestIncrement: Long,
  ackCallback: ACKCallback[C]
)
extends Subscriber[MessagePayload[C]] with Publisher[MessagePayload[C]] {

  //PUBLISHER
  private var downStreamSubscription: Option[MultiplexSubscription[C]] = None
  private var downStreamSubscriber: Option[Subscriber[_ >: MessagePayload[C]]] = None

  def subscribe(s: Subscriber[_ >: MessagePayload[C]]) {
    downStreamSubscriber = Some(s)
    (downStreamSubscriber, upstreamSubscription) match {
      case (Some(dSubscriber), Some(uSubscription)) =>
        val subscription = new MultiplexSubscription[C](
          queuedForSendLimit,
          requestIncrement,
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
          queuedForSendLimit,
          requestIncrement,
          uSubscription,
          dSubscriber,
          ackCallback
        )
        downStreamSubscription = Some(subscription)
        dSubscriber.onSubscribe(subscription)
      case _ =>
    }
  }

  def onNext(t: MessagePayload[C]) {
    downStreamSubscription.foreach { _.onUpStreamMessageReceived(t) }
  }

  def onComplete() {
    downStreamSubscription.foreach { _.cancel() }
  }

  def onError(t: Throwable) {
    downStreamSubscription.foreach { _.cancel() }
  }

}

class MultiplexSubscription[C](
  queuedForSendLimit: Long,
  requestIncrement: Long,
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


  upstreamSubscription.request(requestIncrement)

  def request(n: Long) {
    //the downstream is asking for messages
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
      val shouldDeliver = atomic { implicit tx =>
        val newDemand = downstreamDemand.transformAndGet { _ - items.length }
        isRunning.transformAndGet { _ => newDemand > 0 && pendingPayloads().nonEmpty }
      }
      if(shouldDeliver) {
        deliver()
      }
    }
    else {
      atomic { implicit tx =>
        isRunning.set(false)
      }
    }
  }


  def onUpStreamMessageReceived(payload: MessagePayload[C]) {
    atomic { implicit tx =>
      val mMap = messageMap()
      mMap.get(payload.messageId) match {
        case Some((m, ct, context)) =>
          messageMap.set(mMap + (payload.messageId -> (m, ct + 1L, context)))
        case _ =>
          messageMap.set(mMap + (payload.messageId -> (payload.message, 1L, payload.context)))
      }
      val (sub, subMessages) = subscriptionMap().getOrElse(payload.subscription.id, (payload.subscription, List.empty))
      val shouldAddToPending = subMessages.nonEmpty
      if(shouldAddToPending) {
        pendingPayloads.transform { _ ++ List(payload) }
      }
      subscriptionMap.transform { _ + (sub.id -> (sub, subMessages ++ List((payload.messageId, shouldAddToPending)))) }
    }
    deliveryRequested()
  }

  def onDownStreamMessageComplete(subscriptionId: SubscriptionId, messageId: MessageId) {
    val ackRequired = atomic { implicit tx =>
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
      ackRequired
    }
    if(ackRequired) {
      //TODO: //ACK message
    }
    deliveryRequested()
  }


}
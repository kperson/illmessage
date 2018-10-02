package com.github.kperson.routing

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.github.kperson.model.Message
import com.github.kperson.routing.Multiplex._
import org.reactivestreams.{Publisher, Subscriber, Subscription}
import com.github.kperson.model.MessageSubscription

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.stm.{atomic, Ref}


case class MessagePayload[C](message: Message, subscription: MessageSubscription, messageId: MessageId, context: C)
case class MessageSubscriptions[C](message: Message, subscriptions: List[MessageSubscription], messageId: MessageId, context: C)


trait ACKCallback[C] {

  def ack(messageId: String, context: C): Future[Any]

}

object Multiplex {

  type MessageId = String
  type SubscriptionId = String
  type MessageCount = Long
  type IsPendingDelivery = Boolean

  def flow[C](
    ackCallback: ACKCallback[C]
 )(implicit ec: ExecutionContext): (Flow[MessageSubscriptions[C], MessagePayload[C], NotUsed], (String, String) => Unit) = {
    val multi = new Multiplex[C](ackCallback)
    (Flow.fromSinkAndSource(Sink.fromSubscriber(multi), Source.fromPublisher(multi)), multi.onMessageSent)
  }

}


class Multiplex[C](ackCallback: ACKCallback[C])(implicit ec: ExecutionContext) extends Subscriber[MessageSubscriptions[C]] with Publisher[MessagePayload[C]] {

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
)(implicit ec: ExecutionContext) extends Subscription {

  val downstreamDemand = Ref(0L)
  val messageMap = Ref(Map[String, (Message, MessageCount, C)]())
  val subscriptionMap = Ref(Map[SubscriptionId, (MessageSubscription, List[(MessageId, IsPendingDelivery)])]())
  val pendingPayloads = Ref(List[MessagePayload[C]]())
  val isRunning = Ref(false)

  private var isOpen = true
  private var upstreamComplete = false
  private val maxPerSubscriptionBatch = 10


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

  private def nextMessages(maxNum: Int, list: List[MessagePayload[C]], base: List[MessagePayload[C]] = List.empty): List[MessagePayload[C]] = {
    if(maxNum == 0 || list.isEmpty) {
      base
    }
    else {
      val firstSubscriptionId = list.head.subscription.id
      val items = list.filter { _.subscription.id == firstSubscriptionId }.take(math.min(maxNum, maxPerSubscriptionBatch))
      var size = 0
      //limit the total size to 245KB
      val itemsAdjustedForSize = items.takeWhile { i =>
        size = size + i.message.body.length
        size <= 1024 * 256
      }
      val nextItems = items.filter { _.subscription.id != firstSubscriptionId }
      nextMessages(maxNum - itemsAdjustedForSize.length, nextItems, base ++ itemsAdjustedForSize)
    }
  }

  private def deliver() {
    val items = atomic { implicit tx =>
      val hasDemand = downstreamDemand() > 0
      val isR = isRunning() && hasDemand
      val pending = pendingPayloads()
      val items = if(isR && pending.nonEmpty) {
        val dequeueAmount = math.min(downstreamDemand(), pending.length)
        val next = nextMessages(dequeueAmount.toInt, pending)
        pendingPayloads.set(pending.filter { !next.contains(_) })
        next
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
      groupedPayload.subscriptions.foreach { subscription =>
        val mMap = messageMap()
        mMap.get(groupedPayload.messageId) match {
          case Some((m, ct, context)) =>
            //increment the number of subscribers to the message
            messageMap.set(mMap + (groupedPayload.messageId -> (m, ct + 1L, context)))
          case _ =>
            //if a message is not in our system, just add it
            messageMap.set(mMap + (groupedPayload.messageId -> (groupedPayload.message, 1L, groupedPayload.context)))
        }
        val (_, subMessages) = subscriptionMap().getOrElse(subscription.id, (subscription, List.empty))
        val shouldAddToPending = subMessages.isEmpty

        //you can keep adding for direct delivery up to the batch size as long messages aren't currently being sent
        if (shouldAddToPending) {
          val payload = MessagePayload(groupedPayload.message, subscription, groupedPayload.messageId, groupedPayload.context)
          pendingPayloads.transform { _ ++ List(payload) }
        }
        //add the message to the subscription map
        subscriptionMap.transform {
          _ + (subscription.id -> (subscription, subMessages ++ List((groupedPayload.messageId, shouldAddToPending))))
        }
      }
    }
    deliveryRequested()
  }

  def onDownStreamMessageComplete(subscriptionId: SubscriptionId, messageId: MessageId) {
    val (ackRequired, context) = atomic { implicit tx =>
      val mMap = messageMap()
      val (sentMessage, remainingCount, completedMessageContext) = mMap(messageId)
      val ackRequired = remainingCount == 1L
      if(ackRequired) {
        messageMap.set(mMap - messageId)
      }
      else {
        messageMap.set(mMap + (messageId -> (sentMessage, remainingCount - 1L, completedMessageContext)))
      }

      val sMap = subscriptionMap()
      val (subscription, allMessages) = sMap(subscriptionId)

      val remainingMessages = allMessages.filter { case (mId, _) => mId != messageId }
      val hasPendingDeliveries = remainingMessages.find  { case (_, isPending) => isPending }.isDefined


      if(remainingMessages.isEmpty) {
        //if there are no more message in this subscription, just delete the reference
        subscriptionMap.set(sMap - subscriptionId)
      }
      //once all pending deliveries are complete, we can allow more messages to enter
      else if(!hasPendingDeliveries) {
        val toBePending = remainingMessages.take(maxPerSubscriptionBatch)
        val adjustedRemainingMessages = toBePending.map { case (mId, _) => (mId, true) } ++ remainingMessages.drop(toBePending.length)
        subscriptionMap.set(sMap + (subscriptionId -> (subscription, adjustedRemainingMessages)))

        val toBePendingPayloads = toBePending.map { case (mId, _) =>
          MessagePayload(
            mMap(mId)._1,
            subscription,
            mId,
            mMap(mId)._3
          )
        }
        pendingPayloads.transform { _ ++ toBePendingPayloads }
      }
      else {
        subscriptionMap.set(sMap + (subscriptionId -> (subscription, remainingMessages)))
      }
      (ackRequired, completedMessageContext)
    }
    if(ackRequired) {
      val f = ackCallback.ack(messageId, context)
      f.foreach { _ =>
        checkCompletion()
        deliveryRequested()
      }
      f.failed.foreach { ex =>
        ex.printStackTrace()
      }
    }
    else {
      checkCompletion()
      deliveryRequested()
    }
  }


}
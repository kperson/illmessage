package com.github.kperson.routing

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.github.kperson.model.Message
import com.github.kperson.routing.Multiplex._
import com.github.kperson.model.MessageSubscription
import org.reactivestreams.{Publisher, Subscriber, Subscription}

import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.stm.{atomic, InTxn, Ref}


case class MessagePayload(message: Message, subscription: MessageSubscription, messageId: MessageId)
case class MessageSubscriptions(message: Message, subscriptions: List[MessageSubscription], messageId: MessageId)


trait ACKCallback {

  def ack(messageId: String): Future[Any]

}

object Multiplex {

  type MessageId = String
  type SubscriptionId = String
  type MessageCount = Long
  type IsPendingDelivery = Boolean
  type SubscriptionPayload = List[MessagePayload]

  def flow(
    ackCallback: ACKCallback
 )(implicit ec: ExecutionContext): (Flow[MessageSubscriptions, SubscriptionPayload, NotUsed], (String, String) => Unit) = {
    val multi = new Multiplex(ackCallback)
    (Flow.fromSinkAndSource(Sink.fromSubscriber(multi), Source.fromPublisher(multi)), multi.onMessageSent)
  }

}


class Multiplex(ackCallback: ACKCallback)(implicit ec: ExecutionContext) extends Subscriber[MessageSubscriptions] with Publisher[SubscriptionPayload] {

  //PUBLISHER
  private var downStreamSubscription: Option[MultiplexSubscription] = None
  private var downStreamSubscriber: Option[Subscriber[_ >: SubscriptionPayload]] = None



  def onMessageSent(subscriptionId: SubscriptionId, messageId: MessageId) {
    downStreamSubscription.foreach { _.onDownStreamMessageComplete(subscriptionId, messageId) }
  }

  def subscribe(s: Subscriber[_ >: SubscriptionPayload]) {
    downStreamSubscriber = Some(s)
    (downStreamSubscriber, upstreamSubscription) match {
      case (Some(dSubscriber), Some(uSubscription)) =>
        val subscription = new MultiplexSubscription(
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
        val subscription = new MultiplexSubscription(
          uSubscription,
          dSubscriber,
          ackCallback
        )
        downStreamSubscription = Some(subscription)
        dSubscriber.onSubscribe(subscription)
      case _ =>
    }
  }

  def onNext(t: MessageSubscriptions) {

    downStreamSubscription.foreach { _.onUpStreamMessageReceived(t) }

  }

  def onComplete() {
  }

  def onError(t: Throwable) {
    downStreamSubscriber.foreach { _.onError(t) }
  }

}

object MultiplexSubscription {

  implicit class RichSubscriptionMap(self: Map[SubscriptionId, (MessageSubscription, List[(MessageId, MessageStatus)])]) {

    def messages(subscriptionId: SubscriptionId): List[(MessageId, MessageStatus)] = {
      val messages = self.get(subscriptionId).map { case (_, list) => list }
      messages.getOrElse(List.empty)
    }

    def messagesOfType(subscriptionId: SubscriptionId, status: MessageStatus): List[(MessageId, MessageStatus)] = {
      messages(subscriptionId).filter { case (_, st) => status == st }
    }

    def hasMessageType(subscriptionId: SubscriptionId, status: MessageStatus): Boolean = {
      messagesOfType(subscriptionId, status).nonEmpty
    }

    def hasAvailableForSend: Boolean = {
      val subscriptions = self.map { case (_, (subscription, _)) =>  subscription }
      subscriptions.map { subscription =>
        hasMessageType(subscription.id, ReadyForSend)
      }.nonEmpty
    }

    def hasInFlight: Boolean = {
      val subscriptions = self.map { case (_, (subscription, _)) =>  subscription }
      subscriptions.map { subscription =>
        hasMessageType(subscription.id, InFlight)
      }.nonEmpty
    }

    def hasQueued: Boolean = {
      val subscriptions = self.map { case (_, (subscription, _)) =>  subscription }
      subscriptions.map { subscription =>
        hasMessageType(subscription.id, Queued)
      }.nonEmpty
    }

    def availableForSend(messageMap: Map[String, (Message, MessageCount)]) : List[MessagePayload] = {
      val subscriptions = self.map { case (_, (subscription, _)) =>  subscription }
      subscriptions.flatMap { subscription =>
        messagesOfType(subscription.id, ReadyForSend).map { case (mId, _) =>
          MessagePayload(
            messageMap(mId)._1,
            subscription,
            mId
          )
        }
      }.toList
    }

    def queued(messageMap: Map[String, (Message, MessageCount)]) : List[MessagePayload] = {
      val subscriptions = self.map { case (_, (subscription, _)) =>  subscription }
      subscriptions.flatMap { subscription =>
        messagesOfType(subscription.id, Queued).map { case (mId, _) =>
          MessagePayload(
            messageMap(mId)._1,
            subscription,
            mId
          )
        }
      }.toList
    }

    def inFLight(messageMap: Map[String, (Message, MessageCount)]) : List[MessagePayload] = {
      val subscriptions = self.map { case (_, (subscription, _)) =>  subscription }
      subscriptions.flatMap { subscription =>
        messagesOfType(subscription.id, InFlight).map { case (mId, _) =>
          MessagePayload(
            messageMap(mId)._1,
            subscription,
            mId
          )
        }
      }.toList
    }


    def stats(messageMap: Map[String, (Message, MessageCount)]) {
      println(s"inflight = ${inFLight(messageMap).size}, queue = ${queued(messageMap).size}, ready for send = ${availableForSend(messageMap).size}")
    }


    final def transferToInFlight(payloads: List[MessagePayload], next: Option[Map[SubscriptionId, (MessageSubscription, List[(MessageId, MessageStatus)])]] = None): Map[SubscriptionId, (MessageSubscription, List[(MessageId, MessageStatus)])] = {
      val sMap = next.getOrElse(self)
      payloads match {
        case head :: tail =>
          val subMessages = sMap.messages(head.subscription.id)
          val indexOfMessage = subMessages.indexWhere { case (mId, _) => mId == head.messageId }
          val updatedMessages = subMessages.updated(indexOfMessage, (head.messageId, InFlight))
          val results = sMap + (head.subscription.id -> (head.subscription, updatedMessages))
          transferToInFlight(tail, Some(results))
        case Nil =>
          sMap
      }
    }

  }

}

sealed trait MessageStatus

object Queued extends MessageStatus
object ReadyForSend extends MessageStatus
object InFlight extends MessageStatus


class MultiplexSubscription(
  upstreamSubscription: Subscription,
  downstreamSubscriber: Subscriber[_ >: SubscriptionPayload],
  ackCallback: ACKCallback,
)(implicit ec: ExecutionContext) extends Subscription {

  import MultiplexSubscription._

  val internalDemand = Ref(0L)
  val downstreamDemand = Ref(0L)
  val messageMap = Ref(Map[String, (Message, MessageCount)]())
  val subscriptionMap = Ref(Map[SubscriptionId, (MessageSubscription, List[(MessageId, MessageStatus)])]())
  val isRunning = Ref(false)

  private var isOpen = true
  private val maxPerSubscriptionBatch = 10

  private var hasRequestedUpstream = false

  def request(n: Long) {
    if(!hasRequestedUpstream) {
      hasRequestedUpstream = true
      atomic { implicit tx =>
        internalDemand.transform { _ + n }
      }
      upstreamSubscription.request(n)
    }


    atomic { implicit tx =>
      downstreamDemand.transformAndGet(_ + n)
    }
    deliveryRequested()
  }

  def cancel() {
    isOpen = false
  }

  private def deliveryRequested() {
    val shouldDeliver = atomic { implicit tx =>
      if (downstreamDemand() > 0 && subscriptionMap().hasAvailableForSend) {
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


  private def nextMessages(maxNum: Long, list: List[MessagePayload], base: List[MessagePayload] = List.empty): List[SubscriptionPayload] = {
    if(maxNum == 0 || list.isEmpty) {
      val subscriptionIds = base.map { _.subscription.id }.distinct
      val groupedItems = base.groupBy { _.subscription.id }
      subscriptionIds.map { groupedItems(_) }
    }
    else {
      val firstSubscriptionId = list.head.subscription.id
      val items = list.filter { _.subscription.id == firstSubscriptionId }.take(maxPerSubscriptionBatch)
      var size = 0
      //limit the total size to 245KB
      val itemsAdjustedForSize = items.takeWhile { i =>
        size = size + i.message.body.length
        size <= 1024 * 256
      }
      val nextItems = list.filter { _.subscription.id != firstSubscriptionId }
      nextMessages(maxNum - 1, nextItems, base ++ itemsAdjustedForSize)
    }

  }

  private def deliver() {

    val items = atomic { implicit tx =>
      println("PRE OUTBOX")
      subscriptionMap().stats(messageMap())
      val shouldRun = isRunning() && downstreamDemand() > 0
      val nextItems = if(shouldRun) {
        val available = subscriptionMap().availableForSend(messageMap())
        val next = nextMessages(downstreamDemand(), available)
        val nextFlat = next.flatMap { x => x }
        val updatedSubscriptionMap = subscriptionMap().transferToInFlight(nextFlat)
        subscriptionMap.set(updatedSubscriptionMap)
        downstreamDemand.transform { _ - next.length }
        println("JUST ABOUT GO TO OUTBOX")
        subscriptionMap().stats(messageMap())
        next
      }
      else {
        List.empty
      }
      nextItems
    }

    items.foreach { i =>
      downstreamSubscriber.onNext(i)
    }

    atomic { implicit tx =>
      isRunning.set(items.nonEmpty)
      println("SENT TO OUTBOX")
      subscriptionMap().stats(messageMap())

    }
    if(items.nonEmpty) {
      deliver()
    }
  }

  def onUpStreamMessageReceived(groupedPayload: MessageSubscriptions) {
    atomic { implicit tx =>
      groupedPayload.subscriptions.foreach { subscription =>
        val mMap = messageMap()
        mMap.get(groupedPayload.messageId) match {
          case Some((m, ct)) =>
            //increment the number of subscribers to the message
            messageMap.set(mMap + (groupedPayload.messageId -> (m, ct + 1L)))
          case _ =>
            //if a message is not in our system, just add it
            messageMap.set(mMap + (groupedPayload.messageId -> (groupedPayload.message, 1L)))
        }
        val (_, subMessages) = subscriptionMap().getOrElse(subscription.id, (subscription, List.empty))

        subscriptionMap.transform {
          _ + (subscription.id -> (subscription, subMessages ++ List((groupedPayload.messageId, Queued))))
        }
      }
      transferToReadyForSend(groupedPayload.subscriptions.map { _.id })
    }
    upstreamSubscription.request(1)
    deliveryRequested()
  }

  private def transferToReadyForSend(subscriptionIds: List[String])(implicit txn: InTxn) {
    subscriptionIds.foreach { subscriptionId =>
      val subMap = subscriptionMap()
      subMap.get(subscriptionId).foreach { case (subscription, _) =>
        if (!subMap.hasMessageType(subscriptionId, InFlight)) {
          val readyForSend = subMap.messagesOfType(subscriptionId, ReadyForSend)

          val queued = subMap.messagesOfType(subscriptionId, Queued)
          val toTransfer = queued.take(maxPerSubscriptionBatch).map { case (mId, _) => (mId, ReadyForSend) }

          val newSubscriptionMessages = readyForSend ++ toTransfer ++ queued.drop(toTransfer.length)
          subscriptionMap.transform { _ + (subscriptionId -> (subscription, newSubscriptionMessages)) }
        }
      }
    }
  }

  def onDownStreamMessageComplete(subscriptionId: SubscriptionId, messageId: MessageId) {
    val ackRequired = atomic { implicit tx =>
      val mMap = messageMap()
      val sMap = subscriptionMap()
      val (sentMessage, remainingCount) = mMap(messageId)
      val ackRequired = remainingCount == 1L
      if(ackRequired) {
        messageMap.set(mMap - messageId)
      }
      else {
        messageMap.set(mMap + (messageId -> (sentMessage, remainingCount - 1L)))
      }

      val (subscription, allMessages) = sMap(subscriptionId)
      val remainingMessages = allMessages.filter { case (mId, _) => mId != messageId }

      if(remainingMessages.isEmpty) {
        //if there are no more message in this subscription, just delete the reference
        subscriptionMap.set(sMap - subscriptionId)
      }
      else {
        //update the messages map and attempt transfer
        subscriptionMap.set(sMap + (subscriptionId -> (subscription, remainingMessages)))
        transferToReadyForSend(List(subscriptionId))
      }
      println("POST DELIVER")
      subscriptionMap().stats(messageMap())
      ackRequired
    }
    if(ackRequired) {
      val f = ackCallback.ack(messageId)
      f.foreach { _ =>
        deliveryRequested()
      }
      f.failed.foreach { ex =>
        ex.printStackTrace()
      }
    }
    else {
      deliveryRequested()
    }
  }


}
package com.github.kperson.wal

import com.github.kperson.model.{Message, MessageSubscription}
import org.reactivestreams.{Publisher, Subscriber, Subscription}

import scala.concurrent.ExecutionContext
import scala.concurrent.stm.{atomic, Ref}

trait Transfer {

  def ttl: Long
  def messages: List[Message]
  def onTransfer()
  def preComputedSubscription: Option[MessageSubscription]
  def messageId: Option[String]

}

object WALTransfer {

  type WALOutPayload = (Option[MessageSubscription], List[(Message, String)])

}

class WALTransfer(
  wal: WAL
)(implicit ec: ExecutionContext) extends Subscription {

  val demand = Ref(0L)
  val transfers = Ref(List[Transfer]())
  val isRunning = Ref(false)

  var subscriber: Option[Subscriber[_ >: WALTransfer.WALOutPayload]] = None

  def add(transfer: Transfer) {
    atomic { implicit tx =>
      transfers.transform { _ ++ List(transfer) }
    }
    deliveryRequested()
  }

  private def deliveryRequested() {
    val shouldDeliver = atomic { implicit tx =>
      if (demand() > 0 && transfers().nonEmpty) {
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
    val item = atomic { implicit tx =>
      val demandNum = demand()
      val hasDemand = demandNum > 0
      val pendingTransfers = transfers()
      val applicable = pendingTransfers.filter { _.ttl >= System.currentTimeMillis }
      val isR = isRunning() && hasDemand && applicable.nonEmpty
      val item = if(isR) {
        val next = applicable.headOption
        transfers.set(applicable.drop(1))
        demand.transform { _ - 1 }
        next
      }
      else {
        None
      }
      item
    }
    item match {
      case Some(i) =>
        (i.preComputedSubscription, i.messageId) match {
          case (Some(sub), Some(mId)) if i.messages.length == 1 =>
            val payload: WALTransfer.WALOutPayload = (Some(sub), List((i.messages.head, mId)))
            subscriber.foreach { _.onNext(payload) }
            i.onTransfer()
            deliver()
          case _ =>
            wal.write(i.messages).map { ids =>
              val payload: WALTransfer.WALOutPayload = (i.preComputedSubscription, i.messages.zip(ids))
              subscriber.foreach { _.onNext(payload) }
              i.onTransfer()
              deliver()
            }
        }
      case _ =>
        atomic { implicit tx =>
          isRunning.set(false)
        }
        deliveryRequested()
    }
  }

  def request(n: Long) {
    atomic { implicit tx =>
      demand.transform { _ + n }
    }
    deliveryRequested()
  }

  def cancel() {
  }


}

class WALTransferPublisher(walTransfer: WALTransfer) extends Publisher[WALTransfer.WALOutPayload] {

  def subscribe(s: Subscriber[_ >: WALTransfer.WALOutPayload]) {
    walTransfer.subscriber = Some(s)
    s.onSubscribe(walTransfer)
  }

}


package com.github.kperson.aws.sqs

import java.util.{Timer, TimerTask}

import org.reactivestreams.{Subscriber, Subscription}

import scala.concurrent.duration._
import scala.concurrent.stm.{atomic, Ref}
import scala.concurrent.ExecutionContext.Implicits.global


class SQSSubscription(
 subscriber: Subscriber[_ >: SNSMessage[String]],
 queueClient: SQSQueueClient,
 queueName: String,
 autoDelete: Boolean = false,
 backOffStrategy: Option[BackOffStrategy] = None
) extends Subscription {

  val demand = Ref(0L)
  val isRunning = Ref(false)
  private var isOpen = true
  val maxFetchAmount = 5
  private var nextTimeout = backOffStrategy.map { _.initialTimeout }


  def request(n: Long) {
    demand.single.transformAndGet(_ + n)
    deliveryRequested()
  }

  private def deliveryRequested() {
    val shouldFetch = atomic { implicit tx =>
      if(demand() > 0) {
        isRunning.transformAndExtract { ir => (true, !ir) }
      }
      else {
        false
      }
    }
    if(shouldFetch) {
      fetchMessages()
    }
  }

  private def fetchMessages() {
    val (currentDemand, isR) = atomic { implicit tx =>
      val hasDemand = demand() > 0
      val isR = isRunning() && hasDemand
      isRunning.set(isR)
      (demand(), isR)
    }
    if(isR && isOpen) {
      val fetchAmount = math.min(maxFetchAmount, currentDemand)
      val remoteFetch = queueClient.fetchMessages(queueName, Some(10.seconds), maxNumberOfMessages = fetchAmount.toInt)
      remoteFetch.onSuccess { case res =>
        if(!res.isEmpty) {
          //reset the back off
          nextTimeout = backOffStrategy.map { _.initialTimeout }
        }
        if(isOpen) {
          atomic { implicit tx =>
            demand.transform { _ - res.length }
          }
          res.foreach(subscriber.onNext(_))
          if(autoDelete) {
            res.foreach(m => queueClient.deleteMessage(queueName, m.receiptHandle))
          }
          (backOffStrategy, nextTimeout) match {
            case (Some(s), Some(t)) if res.isEmpty =>
              //schedule the fetch
              val timer = new Timer()
              timer.schedule(new TimerTask {
                def run() {
                  //double the back off, up to the max
                  nextTimeout = Some(if(t * 2 > s.maxTimeout) s.maxTimeout else t * 2)
                  fetchMessages()
                }
              }, t.toMillis)
            case _ =>
              //do the next fetch immediately
              fetchMessages()
          }
        }
      }
      remoteFetch.onFailure { case ex =>
        isOpen = false
        subscriber.onError(ex)
      }
    }
  }

  def cancel() {
    isOpen = false
  }

}

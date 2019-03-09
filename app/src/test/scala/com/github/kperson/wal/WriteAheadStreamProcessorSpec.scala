package com.github.kperson.wal

import com.github.kperson.delivery.DeliveryDAO
import com.github.kperson.model.{Message, MessageSubscription}
import com.github.kperson.subscription.SubscriptionDAO
import com.github.kperson.test.spec.IllMessageSpec

import scala.concurrent.{ExecutionContext, Future}


class WriteAheadStreamProcessorSpec extends IllMessageSpec {

  val message = Message(
    "my-r-key-1",
    "hello world",
    "exchange-one",
    None,
    "group-one"
  )

  val walRecord = WALRecord(message, "id-one", None)

  val subscription = MessageSubscription(message.exchange, message.routingKey, "q1", "1234")

  def withMocks(testCode: WriteAheadStreamProcessor => Any)  {
   testCode(new WriteAheadStreamProcessor {
     implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

     val subscriptionDAO: SubscriptionDAO =  mock[SubscriptionDAO]

     val walDAO: WriteAheadDAO = mock[WriteAheadDAO]
     (walDAO.remove _)
       .expects("id-one", message.partitionKey)
       .returning(Future.successful(true))


     val deliveryDAO: DeliveryDAO = mock[DeliveryDAO]
     (deliveryDAO.queueMessages _)
       .expects(List(subscription), walRecord)
       .returning(Future.successful(true))
    })
  }


  "MessageProcessor" should "deliver messages" in withMocks { processor =>
    (processor.subscriptionDAO.fetchSubscriptions _)
      .expects(message.exchange, message.routingKey)
      .returning(Future.successful(List(subscription)))

    val job = processor.handleNewWALRecord(walRecord)
    whenReady(job, secondsTimeOut(3)) { _ => }
  }

  it should "use pre computed subscriptions" in withMocks { processor =>
    val walRecordPreComputed = walRecord.copy(preComputedSubscription = Some(subscription))

    val job = processor.handleNewWALRecord(walRecordPreComputed)
    whenReady(job, secondsTimeOut(3)) { _ => }
  }

}

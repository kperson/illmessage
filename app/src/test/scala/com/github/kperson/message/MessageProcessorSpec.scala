package com.github.kperson.message

import com.github.kperson.model.{Message, MessageSubscription}
import com.github.kperson.subscription.SubscriptionDAO
import com.github.kperson.test.spec.IllMessageSpec
import com.github.kperson.wal.{WALRecord, WriteAheadDAO}

import scala.concurrent.{ExecutionContext, Future}



class MessageProcessorSpec extends IllMessageSpec {

  val message = Message(
    "my-r-key-1",
    "hello world",
    "exchange-one",
    None,
    "group-one"
  )

  val walRecord = WALRecord(message, "id-one", None)

  val subscription = MessageSubscription(message.exchange, message.routingKey, "q1", "1234", "active")

  def withMocks(testCode: (MessageProcessor) => Any)  {
   testCode(new MessageProcessor {
      implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

      lazy val subscriptionDAO: SubscriptionDAO =  mock[SubscriptionDAO]


     val wMock = mock[WriteAheadDAO]
     (wMock.remove _)
       .expects("id-one", message.partitionKey)
       .returning(Future.successful(true))
      lazy val walDAO: WriteAheadDAO = wMock

     val qMock = mock[QueueClient]
     (qMock.sendMessages _)
       .expects(List(subscription), walRecord)
       .returning(Future.successful(true))

      lazy val queueClient: QueueClient = qMock
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

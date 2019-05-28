package com.github.kperson.delivery

import com.github.kperson.aws.dynamo.{ChangeCapture, New, Update}
import com.github.kperson.message.QueueClient
import com.github.kperson.model.{Message, MessageSubscription}
import com.github.kperson.test.spec.IllMessageSpec

import scala.concurrent.{ExecutionContext, Future}

class DeliveryStreamProcessorSpec extends IllMessageSpec {


  val message = Message(
    "my-r-key-1",
    "hello world",
    "exchange-one",
    "group-one"
  )

  val subscription = MessageSubscription(
    "e1",
    "com.*.hello",
    "q1",
    "782056314912",
    "active"
  )


  val delivery = Delivery(message, subscription, 2, "inFlight", "m1")
  val pendingDelivery = delivery.copy(status = "pending")


  def withMocks(testDelivery: Delivery, repeat: Range)(testCode: DeliveryStreamProcessor => Any) {
    testCode(new DeliveryStreamProcessor {
      implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

      val queueClient: QueueClient = mock[QueueClient]
      (queueClient.sendMessage _)
        .expects(testDelivery)
        .returning(Future.successful(true))
        .repeat(repeat)

      val deliveryDAO: DeliveryDAO = mock[DeliveryDAO]
      (deliveryDAO.remove _)
        .expects(testDelivery)
        .returning(Future.successful(true))
        .repeat(repeat)
    })
  }


  "DeliveryStreamProcessorSpec" should "process new inFlight deliveries" in withMocks(delivery, 1 to 1) { processor =>
    val cc: ChangeCapture[Delivery] = New("SOURCE", delivery)
    whenReady(processor.handleDelivery(cc), secondsTimeOut(3)) { _ =>
      true should be (true)
    }
  }

  it should "ignore pending deliveries" in withMocks(pendingDelivery, 0 to 0) { processor =>
    val cc: ChangeCapture[Delivery] = New("SOURCE", pendingDelivery)
    whenReady(processor.handleDelivery(cc), secondsTimeOut(3)) { _ =>
      true should be (true)
    }
  }

  it should "process updated inFlight deliveries" in withMocks(delivery, 1 to 1) { processor =>
    val cc: ChangeCapture[Delivery] = Update("SOURCE", pendingDelivery, delivery)
    whenReady(processor.handleDelivery(cc), secondsTimeOut(3)) { _ =>
      true should be (true)
    }
  }

}

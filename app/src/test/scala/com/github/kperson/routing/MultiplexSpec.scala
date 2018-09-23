package com.github.kperson.routing

import akka.stream.scaladsl.{Sink, Source}
import akka.stream.testkit.scaladsl.TestSink
import com.github.kperson.model.{Message, Subscription}
import com.github.kperson.util.AkkaContext
import org.scalamock.scalatest.MockFactory
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.duration._
import scala.concurrent.Await


class MultiplexSpec extends FlatSpec with Matchers with AkkaContext with MockFactory {

  val subscriptionOne = Subscription("e1", "abc.*", "q1", "23232328")
  val subscriptionTwo = Subscription("e1", "abc.*", "q2", "23232327")
  val messageOne = Message("abc.123", "b1", "e1")
  val messageTwo = Message("abc.321", "b2", "e1")

  val payloadOne = MessagePayload(messageOne, subscriptionOne, "M1", "HI1")
  val payloadTwo = MessagePayload(messageTwo, subscriptionOne, "M2", "HI2")
  val payloadThree = MessagePayload(messageOne, subscriptionTwo, "M1", "HI1")
  val payloadFour = MessagePayload(messageOne, subscriptionTwo, "M3", "HI3")

  val groupedPayloadOne = MessageSubscriptions(messageOne, List(subscriptionOne, subscriptionTwo), "M1", "HI1")
  val groupedPayloadTwo = MessageSubscriptions(messageTwo, List(subscriptionTwo), "M2", "HI2")
  val groupedPayloadThree = MessageSubscriptions(messageOne, List(subscriptionOne, subscriptionTwo), "M3", "HI3")


  "Multiplex" should "output re-ordered message payloads" in withAkka { implicit materializer =>
    val expected = scala.collection.mutable.Map(
      subscriptionOne.id -> List("M1", "M3"),
      subscriptionTwo.id -> List("M1", "M2", "M3")
    )
    implicit val system = materializer.system
    val ackCallBack = mock[ACKCallback[String]]
    (ackCallBack.ack _).expects("M1", "HI1").noMoreThanOnce().returning(Unit)
    (ackCallBack.ack _).expects("M3", "HI3").noMoreThanOnce.returning(Unit)
    (ackCallBack.ack _).expects("M2", "HI2").noMoreThanOnce.returning(Unit)
    val (flow, completeFunc) = Multiplex.flow(100, 10, ackCallBack)
    val s = Source(List(groupedPayloadOne, groupedPayloadTwo, groupedPayloadThree))
      .via(flow)
      .map { x =>
        completeFunc(x.subscription.id, x.messageId)
        x
      }

    val z = s.runForeach { p =>
      expected(p.subscription.id).head should be (p.messageId)
      expected(p.subscription.id) = expected(p.subscription.id).drop(1)
    }
    Await.result(z, 5.seconds)
    expected.foreach { case (_, v) =>
      v.isEmpty should be (true)
    }
  }

  it should "handle errors" in withAkka { implicit materializer =>
    implicit val system = materializer.system
    val ackCallBack = mock[ACKCallback[String]]
    val (flow, completeFunc) = Multiplex.flow(100, 10, ackCallBack)
    val s = Source(List(groupedPayloadOne, groupedPayloadTwo, groupedPayloadThree))
      .via(flow)
      .map { x =>
        completeFunc(x.subscription.id, x.messageId)
        throw new RuntimeException("IDK")
        x
      }
    s.runWith(TestSink.probe[MessagePayload[String]])
      .request(3)
      .expectError()
  }

}

package com.github.kperson.routing

import akka.stream.scaladsl.{Sink, Source}
import akka.stream.testkit.scaladsl.TestSink
import com.github.kperson.model.{Message, MessageSubscription}
import com.github.kperson.routing.Multiplex.SubscriptionPayload
import com.github.kperson.util.AkkaContext
import com.typesafe.config.ConfigFactory
import org.scalamock.scalatest.MockFactory
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}


class MultiplexSpec extends FlatSpec with Matchers with AkkaContext with MockFactory {

  val subscriptionOne = MessageSubscription("e1", "abc.*", "q1", "23232328")
  val subscriptionTwo = MessageSubscription("e1", "abc.*", "q2", "23232327")
  val messageOne = Message("abc.123", "b1", "e1")
  val messageTwo = Message("abc.321", "b2", "e1")

  val payloadOne = MessagePayload(messageOne, subscriptionOne, "M1")
  val payloadTwo = MessagePayload(messageTwo, subscriptionOne, "M2")
  val payloadThree = MessagePayload(messageOne, subscriptionTwo, "M1")
  val payloadFour = MessagePayload(messageOne, subscriptionTwo, "M3")

  val groupedPayloadOne = MessageSubscriptions(messageOne, List(subscriptionOne, subscriptionTwo), "M1")
  val groupedPayloadTwo = MessageSubscriptions(messageTwo, List(subscriptionTwo), "M2")
  val groupedPayloadThree = MessageSubscriptions(messageOne, List(subscriptionOne, subscriptionTwo), "M3")


  "Multiplex" should "output re-ordered message payloads" in withAkka { implicit materializer =>
    val expected = scala.collection.mutable.Map(
      subscriptionOne.id -> List("M1", "M3"),
      subscriptionTwo.id -> List("M1", "M2", "M3")
    )
    implicit val system = materializer.system
    implicit val ec = materializer.executionContext
    val ackCallBack = mock[ACKCallback]
    (ackCallBack.ack _).expects("M1").once().returning(Future.successful(true))
    (ackCallBack.ack _).expects("M3").once().returning(Future.successful(true))
    (ackCallBack.ack _).expects("M2").once().returning(Future.successful(true))
    val (flow, completeFunc) = Multiplex.flow(ackCallBack)
    val s = Source(List(groupedPayloadOne, groupedPayloadTwo, groupedPayloadThree))
      .via(flow)
      .map { xs =>
        xs.foreach { x =>
          completeFunc(x.subscription.id, x.messageId)
        }
        xs
      }

    val z = s.runForeach { ps =>
      ps.foreach { p =>
        expected(p.subscription.id).head should be(p.messageId)
        expected(p.subscription.id) = expected(p.subscription.id).drop(1)
      }
    }
    Await.result(z, 5.seconds)
    expected.foreach { case (_, v) =>
      v.isEmpty should be (true)
    }
  }

  it should "handle errors" in withAkka { implicit materializer =>
    implicit val system = materializer.system
    implicit val ec = materializer.executionContext
    val ackCallBack = mock[ACKCallback]
    val (flow, completeFunc) = Multiplex.flow(ackCallBack)
    val s = Source(List(groupedPayloadOne, groupedPayloadTwo, groupedPayloadThree))
      .via(flow)
      .map { xs =>
        xs.foreach { x =>
          completeFunc(x.subscription.id, x.messageId)
          throw new RuntimeException("IDK")
        }
        xs
      }
    s.runWith(TestSink.probe[SubscriptionPayload])
      .request(3)
      .expectError()
  }

}

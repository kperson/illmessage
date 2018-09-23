package com.github.kperson.routing

import com.github.kperson.dao.DAO
import com.github.kperson.model.{DeadLetterMessage, Message, Subscription}

import org.scalamock.scalatest.MockFactory

import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}


class RoutingSpec extends FlatSpec with Matchers with MockFactory {

  "Routing" should "deliver messages" in {
    val messageOne = Message("abc.123", "b1", "e1")
    val messageTwo = Message("abc.321", "b2", "e1")
    val subscription = Subscription("e1", "abc.*", "q1", "2323232")

    val dao = stub[DAO]
    (dao.fetchSubscriptions _).when().returns(Future.successful(Map("e1" -> List(subscription))))
    (dao.deliverMessage _).when(messageOne, subscription).returns(Future.successful(Left(true)))
    (dao.deliverMessage _).when(messageTwo, subscription).returns(Future.successful(Left(true)))

    val messages = List(messageOne, messageTwo)
    Await.result( Routing.handle(dao, messages), 5.seconds)
  }

  it should "save dead letter messages" in {
    val messageOne = Message("abc.123", "b1", "e1")
    val messageTwo = Message("abc.321", "b2", "e1")
    val subscription = Subscription("e1", "abc.*", "q1", "2323232")
    val deadLetterMessage = DeadLetterMessage("id1", subscription, messageTwo, "reason", 10L)

    val dao = stub[DAO]
    (dao.fetchSubscriptions _).when().returns(Future.successful(Map("e1" -> List(subscription))))
    (dao.saveDeadLetterMessage _).when(deadLetterMessage).returns(Future.successful(true))
    (dao.deliverMessage _).when(messageOne, subscription).returns(Future.successful(Left(true)))
    (dao.deliverMessage _).when(messageTwo, subscription).returns(Future.successful(Right(deadLetterMessage)))

    val messages = List(messageOne, messageTwo)
    Await.result( Routing.handle(dao, messages), 5.seconds)
  }

  it should "reject mismatched keys" in {
    val messageOne = Message("abc.123", "b1", "e1")
    val messageTwo = Message("w", "b2", "e1")
    val subscription = Subscription("e1", "abc.*", "q1", "2323232")

    val dao = mock[DAO]
    (dao.deliverMessage _).expects(messageOne, subscription).returning(Future.successful(Left(true)))
    (dao.fetchSubscriptions _).expects().returns(Future.successful(Map("e1" -> List(subscription))))

    val messages = List(messageOne, messageTwo)
    Await.result(Routing.handle(dao, messages), 5.seconds)
  }

  it should "not publish to other exchanges" in {
    val messageOne = Message("abc.123", "b1", "e2")
    val subscription = Subscription("e1", "abc.*", "q1", "2323232")

    val dao = stub[DAO]
    (dao.fetchSubscriptions _).when().returns(Future.successful(Map("e1" -> List(subscription))))

    val messages = List(messageOne)
    Await.result(Routing.handle(dao, messages), 5.seconds)

    (dao.deliverMessage _).verify(*, *).never()
  }

}

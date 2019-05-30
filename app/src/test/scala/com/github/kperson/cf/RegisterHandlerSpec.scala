package com.github.kperson.cf

import com.github.kperson.model.MessageSubscription
import com.github.kperson.subscription.SubscriptionDAO
import com.github.kperson.test.spec.IllMessageSpec

import scala.concurrent.{ExecutionContext, Future}

class RegisterHandlerSpec extends IllMessageSpec {

  val subscription = MessageSubscription(
    "e1",
    "com.*.hello",
    "q1",
    "782056314912",
    "active"
  )

  "RegisterHandler" should "handle creates" in {
    val rDAO = mock[CFRegisterDAO]
    (rDAO.saveRegistration _)
      .expects(*, subscription.id, subscription.exchange)
      .returning(Future.successful(true))

    val sDAO = mock[SubscriptionDAO]
    (sDAO.save _)
      .expects(subscription)
      .returning(Future.successful(subscription))

    val handler = new RegisterHandler {
      implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

      def accountId: String = "abc"

      def subscriptionDAO: SubscriptionDAO = sDAO

      def cfRegisterDAO: CFRegisterDAO = rDAO
    }
    val req = CFRequest(
      "Create",
      "ResponseURL",
      "StackId",
      "RequestId",
      None,
      "LogicalResourceId",
      Some(
        Map(
          "Exchange" -> subscription.exchange,
          "BindingKey" -> subscription.bindingKey,
          "Queue" -> subscription.queue,
          "AccountId" -> subscription.accountId
        )
      )
    )
    val expected = CFResponse("SUCCESS", None, "NA", "StackId", "RequestId", "LogicalResourceId")
    whenReady(handler.handleRegisterRequest(req), secondsTimeOut(3)) { rs =>
      rs.copy(PhysicalResourceId = "NA") should be (expected)
    }

  }

  it should "handle updates" in {
    val rDAO = mock[CFRegisterDAO]
    (rDAO.saveRegistration _)
      .expects(*, subscription.id, subscription.exchange)
      .returning(Future.successful(true))

    (rDAO.fetchSubscriptionId _)
      .expects("abc")
      .returning(Future.successful(Some(CFRegistration("abc", subscription.id, subscription.exchange))))

    (rDAO.deleteRegistration _)
      .expects("abc")
      .returning(Future.successful(true))

    val sDAO = mock[SubscriptionDAO]
    (sDAO.save _)
      .expects(subscription)
      .returning(Future.successful(subscription))

    (sDAO.delete _)
      .expects(subscription.exchange, subscription.id)
      .returning(Future.successful(Some(subscription)))

    val handler = new RegisterHandler {
      implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

      def accountId: String = "abc"

      def subscriptionDAO: SubscriptionDAO = sDAO

      def cfRegisterDAO: CFRegisterDAO = rDAO
    }
    val req = CFRequest(
      "Update",
      "ResponseURL",
      "StackId",
      "RequestId",
      Some("abc"),
      "LogicalResourceId",
      Some(
        Map(
          "Exchange" -> subscription.exchange,
          "BindingKey" -> subscription.bindingKey,
          "Queue" -> subscription.queue,
          "AccountId" -> subscription.accountId
        )
      )
    )
    val expected = CFResponse("SUCCESS", None, "abc", "StackId", "RequestId", "LogicalResourceId")
    whenReady(handler.handleRegisterRequest(req), secondsTimeOut(3)) { rs =>
      rs should be (expected)
    }

  }

  it should "handle delete" in {
    val rDAO = mock[CFRegisterDAO]
    (rDAO.fetchSubscriptionId _)
      .expects("abc")
      .returning(Future.successful(Some(CFRegistration("abc", subscription.id, subscription.exchange))))

    (rDAO.deleteRegistration _)
      .expects("abc")
      .returning(Future.successful(true))

    val sDAO = mock[SubscriptionDAO]
    (sDAO.delete _)
      .expects(subscription.exchange, subscription.id)
      .returning(Future.successful(Some(subscription)))

    val handler = new RegisterHandler {
      implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

      def accountId: String = "abc"

      def subscriptionDAO: SubscriptionDAO = sDAO

      def cfRegisterDAO: CFRegisterDAO = rDAO
    }
    val req = CFRequest(
      "Delete",
      "ResponseURL",
      "StackId",
      "RequestId",
      Some("abc"),
      "LogicalResourceId",
      Some(
        Map(
          "Exchange" -> subscription.exchange,
          "BindingKey" -> subscription.bindingKey,
          "Queue" -> subscription.queue,
          "AccountId" -> subscription.accountId
        )
      )
    )
    val expected = CFResponse("SUCCESS", None, "abc", "StackId", "RequestId", "LogicalResourceId")
    whenReady(handler.handleRegisterRequest(req), secondsTimeOut(3)) { rs =>
      rs should be (expected)
    }

  }


}

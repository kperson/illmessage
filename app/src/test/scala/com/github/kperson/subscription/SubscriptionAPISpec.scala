package com.github.kperson.subscription

import akka.http.scaladsl.testkit.ScalatestRouteTest

import com.github.kperson.model.MessageSubscription
import com.github.kperson.test.http.HttpFixturesFixtures
import com.github.kperson.test.spec.IllMessageSpec

import scala.concurrent.Future


class SubscriptionAPISpec extends IllMessageSpec with ScalatestRouteTest {

  val subscription = MessageSubscription(
    "e1",
    "com.*.hello",
    "q1",
    "782056314912",
    "active"
  )


  "SubscriptionAPI" should "register subscriptions" in {
    val mockSubscriptionDAO = mock[SubscriptionDAO]
    val api = new SubscriptionAPI {
      def subscriptionDAO: SubscriptionDAO = mockSubscriptionDAO
    }

    (mockSubscriptionDAO.save _).expects(subscription).returning(Future.successful(subscription))
    val request = HttpFixturesFixtures.request("subscription-request.json", "subscription-create")

    request ~> api.subscriptionRoute ~> check {
      HttpFixturesFixtures.jsonMatchesResponse[Map[String, Any]](
        responseAs[String],
        "subscription-response.json",
        "subscription-response"
      ) should be (true)
    }
  }


  it should "remove subscriptions" in {
    val subscription = MessageSubscription(
      "e1",
      "com.*.hello",
      "q1",
      "782056314912",
      "active"
    )

    val mockSubscriptionDAO = mock[SubscriptionDAO]
    val api = new SubscriptionAPI {
      def subscriptionDAO: SubscriptionDAO = mockSubscriptionDAO
    }

    (mockSubscriptionDAO.delete _).expects("e1", "43i943i439043jv").returning(Future.successful(Some(subscription)))
    val request = HttpFixturesFixtures.request("subscription-request.json", "subscription-delete")

    request ~> api.subscriptionRoute ~> check {
      response.status.intValue() should be(204)
    }
  }


}

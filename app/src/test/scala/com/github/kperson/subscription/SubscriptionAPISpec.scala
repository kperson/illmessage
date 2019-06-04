package com.github.kperson.subscription

import com.github.kperson.lambda.{LambdaHttpRequest, POST}
import com.github.kperson.model.MessageSubscription
import com.github.kperson.test.http.HttpFixtures
import com.github.kperson.test.spec.IllMessageSpec

import scala.concurrent.Future


class SubscriptionAPISpec extends IllMessageSpec {

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
      implicit val ec = scala.concurrent.ExecutionContext.Implicits.global
      def subscriptionDAO: SubscriptionDAO = mockSubscriptionDAO
    }

    (mockSubscriptionDAO.save _).expects(subscription).returning(Future.successful(subscription))

    val requestStr = HttpFixtures.request("subscription-request.json", "subscription-create")
    val request = LambdaHttpRequest(POST, "/subscription", Some(requestStr), isBase64Encoded = false)
    whenReady(api.subscriptionRoute((request.httpMethod, request.path, request))) { rs =>
      HttpFixtures.jsonMatchesResponse[Map[String, Any]](
        rs.body,
        "subscription-response.json",
        "subscription-response"
      ) should be (true)
    }
  }


}

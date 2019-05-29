package com.github.kperson.delivery

import akka.http.scaladsl.testkit.ScalatestRouteTest

import com.github.kperson.test.http.HttpFixturesFixtures
import com.github.kperson.test.spec.IllMessageSpec

import scala.concurrent.Future


class DeliveryAPISpec extends IllMessageSpec with ScalatestRouteTest {

  "DeliveryAPI" should "ack messages" in {

    val request = HttpFixturesFixtures.request("ack-request.json", "ack-post")
    val mockDAO = mock[DeliveryDAO]

    (mockDAO.bulkAck _).expects(*).returning(Future.successful(true))
    val api = new DeliveryAPI  {
      def deliveryDAO: DeliveryDAO = mockDAO
    }

    request ~> api.deliveryRoute ~> check {
      response.status.intValue() should be (204)
    }

  }

}
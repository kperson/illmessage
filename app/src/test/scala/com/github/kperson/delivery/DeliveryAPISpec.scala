package com.github.kperson.delivery

import com.github.kperson.lambda.{LambdaHttpRequest, POST}
import com.github.kperson.test.http.HttpFixtures
import com.github.kperson.test.spec.IllMessageSpec

import scala.concurrent.Future


class DeliveryAPISpec extends IllMessageSpec {

  "DeliveryAPI" should "ack messages" in {

    val requestStr = HttpFixtures.request("ack-request.json", "ack-post")
    val mockDAO = mock[DeliveryDAO]

    (mockDAO.bulkAck _).expects(*).returning(Future.successful(true))
    val api = new DeliveryAPI  {
      implicit val ec = scala.concurrent.ExecutionContext.Implicits.global
      def deliveryDAO: DeliveryDAO = mockDAO
    }

    val request = LambdaHttpRequest(POST, "/ack", Some(requestStr), isBase64Encoded = false)
    whenReady(api.deliveryRoute((request.httpMethod, request.path, request))) { rs =>
      rs.statusCode should be (204)
    }
  }

}
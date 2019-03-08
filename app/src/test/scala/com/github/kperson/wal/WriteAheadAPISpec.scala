package com.github.kperson.wal

import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.github.kperson.test.http.HttpFixturesFixtures
import com.github.kperson.test.spec.IllMessageSpec

import scala.concurrent.Future




class WriteAheadAPISpec extends IllMessageSpec with ScalatestRouteTest {

  "MessageAPI" should "save messages" in {

    val request = HttpFixturesFixtures.request("messages-request.json", "message-post")
    val mockDAO = mock[WriteAheadDAO]

    (mockDAO.write _).expects(*).returning(Future.successful(List("message-id-1", "message-id-2")))
    val api = new WriteAheadAPI {
      def writeAheadDAO: WriteAheadDAO = mockDAO
    }

    request ~> api.writeAheadRoute ~> check {
      HttpFixturesFixtures.jsonMatchesResponse[List[Map[String, String]]](
        responseAs[String],
        "messages-response.json",
        "message-response"
      ) should be (true)
    }

  }

}
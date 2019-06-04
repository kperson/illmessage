package com.github.kperson.wal

import com.github.kperson.test.http.HttpFixtures
import com.github.kperson.test.spec.IllMessageSpec
import com.github.kperson.lambda._

import scala.concurrent.Future


class WriteAheadAPISpec extends IllMessageSpec {

  "MessageAPI" should "save messages" in {

    val requestStr = HttpFixtures.request("messages-request.json", "message-post")
    val mockDAO = mock[WriteAheadDAO]

    (mockDAO.write _).expects(*).returning(Future.successful(List("message-id-1", "message-id-2")))
    val api = new WriteAheadAPI {
      implicit val ec = scala.concurrent.ExecutionContext.Implicits.global
      def writeAheadDAO: WriteAheadDAO = mockDAO
    }

    val request = LambdaHttpRequest(POST, "/messages", Some(requestStr), isBase64Encoded = false)
    whenReady(api.writeAheadRoute((request.httpMethod, request.path, request))) { rs =>
      rs.statusCode should be (204)
    }
  }

}
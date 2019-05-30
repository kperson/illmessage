package com.github.kperson.cf

import com.github.kperson.test.dynamo.DynamoSupport
import com.github.kperson.test.spec.IllMessageSpec

import scala.concurrent.ExecutionContext.Implicits.global


class AmazonCFRegistrationDAOSpec extends IllMessageSpec with DynamoSupport {

  "AmazonCFRegistrationDAO" should "save a registration" in withDynamo { (_, _, client) =>
    val dao = new AmazonCFRegistrationDAO(client, "cfRegistration")

    val f = dao.saveRegistration("pId", "sId", "e1").flatMap { _ =>
      dao.fetchSubscriptionId("pId")
    }
    whenReady(f, secondsTimeOut(3)) { rs =>
      rs should be (Some(CFRegistration("pId", "sId", "e1")))
    }
  }

  it should "delete a registration" in withDynamo { (_, _, client) =>
    val dao = new AmazonCFRegistrationDAO(client, "cfRegistration")
    val f = dao.saveRegistration("pId", "sId", "e1").flatMap { _ =>
      dao.deleteRegistration("pId")
    }.flatMap { _ =>
      dao.fetchSubscriptionId("pId")
    }
    whenReady(f, secondsTimeOut(3)) { rs =>
      rs should be (None)
    }

  }


}

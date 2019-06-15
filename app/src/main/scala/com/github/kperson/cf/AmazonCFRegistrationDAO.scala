package com.github.kperson.cf

import com.github.kperson.aws.dynamo.DynamoClient
import com.github.kperson.serialization._


import scala.concurrent.Future


class AmazonCFRegistrationDAO(
  client: DynamoClient,
  registrationTable: String
) extends CFRegisterDAO {


  def saveRegistration(physicalResourceId: String, subscriptionId: String, exchange: String): Future[Any] = {
    client.putItem(registrationTable, CFRegistration(physicalResourceId, subscriptionId, exchange))
  }

  def deleteRegistration(physicalResourceId: String): Future[Any] = {
    client.deleteItem[CFRegistration](registrationTable, Map("physicalResourceId" -> physicalResourceId))
  }

  def fetchSubscriptionId(physicalResourceId: String): Future[Option[CFRegistration]] = {
    client.getItem[CFRegistration](registrationTable, Map("physicalResourceId" -> physicalResourceId))
  }

}

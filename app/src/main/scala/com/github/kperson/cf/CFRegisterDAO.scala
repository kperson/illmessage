package com.github.kperson.cf

import scala.concurrent.Future


case class CFRegistration(physicalResourceId: String, subscriptionId: String, exchange: String)

trait CFRegisterDAO {

  def saveRegistration(physicalResourceId: String, subscriptionId: String, exchange: String): Future[Any]

  def deleteRegistration(physicalResourceId: String): Future[Any]

  def fetchSubscriptionId(physicalResourceId: String): Future[Option[CFRegistration]]

}

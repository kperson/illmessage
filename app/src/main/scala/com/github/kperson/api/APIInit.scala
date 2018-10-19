package com.github.kperson.api

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import com.github.kperson.app.AppConfig
import com.github.kperson.aws.dynamo.DynamoClient
import com.github.kperson.dao.{AmazonSubscriptionDAO, SubscriptionDAO}
import com.github.kperson.wal.WAL

import scala.concurrent.ExecutionContext


trait APIInit {

  val config = new AppConfig()

  implicit val system: ActorSystem = ActorSystem("app")
  implicit val actorMaterializer: ActorMaterializer = ActorMaterializer()
  implicit val ec: ExecutionContext = actorMaterializer.executionContext

  val dynamoClient: DynamoClient = new DynamoClient(config.awsRegion)
  val wal = new WAL(dynamoClient, config.walTable)
  val subscriptionDAO: SubscriptionDAO = new AmazonSubscriptionDAO(dynamoClient, config.subscriptionTable)


  val api = new API(wal, subscriptionDAO)

}

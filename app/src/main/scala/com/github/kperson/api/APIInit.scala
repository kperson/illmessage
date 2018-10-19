package com.github.kperson.api

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.github.kperson.app.AppConfig
import com.github.kperson.aws.dynamo.DynamoClient
import com.github.kperson.aws.s3.S3Client
import com.github.kperson.dao.AmazonSubscriptionDAO
import com.github.kperson.wal.WAL

import scala.concurrent.ExecutionContext

trait APIInit {

  val config = new AppConfig()

  implicit val system: ActorSystem = ActorSystem("app")
  implicit val actorMaterializer: ActorMaterializer = ActorMaterializer()
  implicit val ec: ExecutionContext = actorMaterializer.executionContext

  val dynamoClient: DynamoClient = new DynamoClient(config.awsRegion)
  val wal = new WAL(dynamoClient, config.walTable)
  val s3Client = new S3Client(config.awsRegion)
  val subscriptionDAO = new AmazonSubscriptionDAO(config.awsBucket, s3Client)

  val api = new API(wal, subscriptionDAO)

}

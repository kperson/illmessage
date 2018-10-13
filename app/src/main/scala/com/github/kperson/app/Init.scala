package com.github.kperson.app

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import com.github.kperson.api.API
import com.github.kperson.aws.dynamo.DynamoClient
import com.github.kperson.aws.s3.S3Client
import com.github.kperson.dao.AmazonSubscriptionDAO
import com.github.kperson.wal.WAL

import scala.concurrent.ExecutionContext


object Init {

  lazy val config = new AppConfig()

  implicit lazy val system: ActorSystem = ActorSystem("app")
  implicit lazy val materializer: ActorMaterializer = ActorMaterializer()
  implicit lazy val ec: ExecutionContext = materializer.executionContext

  lazy val dynamoClient = new DynamoClient(config.awsRegion)
  lazy val wal = new WAL(dynamoClient, config.walTable)
  lazy val s3Client = new S3Client(config.awsRegion)
  lazy val subscriptionDAO = new AmazonSubscriptionDAO(config.awsBucket, s3Client)

  lazy val api = new API(wal, subscriptionDAO)

}

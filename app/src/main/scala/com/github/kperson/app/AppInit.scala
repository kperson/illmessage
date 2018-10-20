package com.github.kperson.app

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import com.github.kperson.aws.dynamo.DynamoClient
import com.github.kperson.aws.sqs.SQSClient
import com.github.kperson.deadletter.{AmazonDeadLetterQueueDAO, DeadLetterQueueDAO}
import com.github.kperson.queue.{AmazonQueueDAO, QueueDAO}
import com.github.kperson.subscription.{AmazonSubscriptionDAO, SubscriptionDAO}
import com.github.kperson.wal.{AmazonWriteAheadDAO, WriteAheadDAO}

import scala.concurrent.ExecutionContext


trait AppInit {

  val config = new AppConfig()

  implicit val system: ActorSystem = ActorSystem("app")
  implicit val actorMaterializer: ActorMaterializer = ActorMaterializer()
  implicit val ec: ExecutionContext = actorMaterializer.executionContext

  val dynamoClient = new DynamoClient(config.awsRegion)
  val sqsClient = new SQSClient(config.awsRegion, "NA")

  val walDAO: WriteAheadDAO = new AmazonWriteAheadDAO(dynamoClient, config.walTable)
  val subscriptionDAO: SubscriptionDAO = new AmazonSubscriptionDAO(dynamoClient, config.subscriptionTable)
  val deadLetterQueueDAO: DeadLetterQueueDAO = new AmazonDeadLetterQueueDAO(dynamoClient, config.deadLetterTable, walDAO)
  val queueDAO: QueueDAO = new AmazonQueueDAO(sqsClient)

}
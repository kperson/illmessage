package com.github.kperson.app

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.github.kperson.aws.dynamo.DynamoClient
import com.github.kperson.aws.ecs.ECSClient
import com.github.kperson.aws.lambda.LambdaClient
import com.github.kperson.aws.sqs.SQSClient
import com.github.kperson.cf.{AmazonCFRegistrationDAO, CFRegisterDAO}
import com.github.kperson.delivery.{AmazonDeliveryDAO, DeliveryDAO}
import com.github.kperson.message.{AmazonQueueClient, QueueClient}
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
  val lambdaClient = new LambdaClient(config.awsRegion)
  val ecsClient = new ECSClient(config.awsRegion)
  val accountId = config.accountId

  val walDAO: WriteAheadDAO = new AmazonWriteAheadDAO(dynamoClient, config.walTable)
  val subscriptionDAO: SubscriptionDAO = new AmazonSubscriptionDAO(dynamoClient, config.subscriptionTable, config.deliveryTable)
  val queueClient: QueueClient = new AmazonQueueClient(sqsClient)
  val cfRegisterDAO: CFRegisterDAO = new AmazonCFRegistrationDAO(dynamoClient, config.cfRegistrationTable)
  val deliveryDAO: DeliveryDAO = new AmazonDeliveryDAO(
    dynamoClient,
    config.deliveryTable,
    config.subscriptionMessageSequenceTable
  )

}
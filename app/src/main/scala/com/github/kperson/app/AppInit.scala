package com.github.kperson.app

import com.github.kperson.aws.dynamo.DynamoClient
import com.github.kperson.aws.sqs.SQSClient
import com.github.kperson.cf.{AmazonCFRegistrationDAO, CFRegisterDAO}
import com.github.kperson.delivery.{AmazonDeliveryDAO, DeliveryDAO}
import com.github.kperson.message.{AmazonQueueClient, QueueClient}
import com.github.kperson.subscription.{AmazonSubscriptionDAO, SubscriptionDAO}
import com.github.kperson.wal.{AmazonWriteAheadDAO, WriteAheadDAO}

import scala.concurrent.ExecutionContext


trait AppInit {

  val config = new AppConfig()

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  val dynamoClient = new DynamoClient(config.awsRegion)
  val sqsClient = new SQSClient(config.awsRegion, "NA")

  val accountId: String = config.accountId
  val walDAO: WriteAheadDAO = new AmazonWriteAheadDAO(dynamoClient, config.walTable)
  val subscriptionDAO: SubscriptionDAO = new AmazonSubscriptionDAO(dynamoClient, config.subscriptionTable, config.deliveryTable)
  val queueClient: QueueClient = new AmazonQueueClient(sqsClient, config.apiEndpoint)
  val cfRegisterDAO: CFRegisterDAO = new AmazonCFRegistrationDAO(dynamoClient, config.cfRegistrationTable)
  val deliveryDAO: DeliveryDAO = new AmazonDeliveryDAO(
    dynamoClient,
    config.deliveryTable,
    config.subscriptionMessageSequenceTable
  )

}
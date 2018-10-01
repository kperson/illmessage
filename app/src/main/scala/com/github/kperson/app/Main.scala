package com.github.kperson.app

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}

import com.github.kperson.aws.dynamo.DynamoClient
import com.github.kperson.aws.s3.S3Client
import com.github.kperson.aws.sqs.SQSClient
import com.github.kperson.dao.AmazonSubscriptionDAO
import com.github.kperson.deadletter.DeadLetterQueue
import com.github.kperson.routing.Multiplex
import com.github.kperson.wal.{WAL, WALTransfer, WALTransferMessage}

import scala.concurrent.ExecutionContext


object Main extends App {

  val config = new AppConfig()

  implicit val system: ActorSystem = ActorSystem("app")
  implicit val materializer: Materializer = ActorMaterializer()
  implicit val ec: ExecutionContext = materializer.executionContext

  try {
    val dynamoClient = new DynamoClient(config.awsRegion)
    val wal = new WAL(dynamoClient, config.walTable)
    val walTransfer = new WALTransfer(wal)
    val s3Client = new S3Client(config.awsRegion)
    val subscriptionDAO = new AmazonSubscriptionDAO(config.awsBucket, s3Client)
    val sqsClient = new SQSClient(config.awsRegion, "N/A")
    val deadLetter = new DeadLetterQueue(dynamoClient, config.deadLetterTable, wal)

    val (orderingFlow, onMessageSent) = Multiplex.flow(new MessageACK(wal))


    //check for wal messages
    val walMessages = wal.load().map { messages =>
      messages.map {  m =>
        new WALTransferMessage(
          m.message,
          m.messageId,
          m.preComputedSubscription
        )
      }
    }

    //load WAL messages before starting the HTTP server
    walMessages.foreach { wm =>
      wm.foreach { walTransfer.add(_) }
      MessageSubscriptionSource(subscriptionDAO, walTransfer)
      .via(orderingFlow)
      .via(MessageDelivery(sqsClient, deadLetter))
      .runForeach { x =>
        onMessageSent(x.subscription.id, x.messageId)
      }

      val httpAdapter = new HttpAdapter(walTransfer, subscriptionDAO)
      httpAdapter.run()
    }

    walMessages.failed.foreach { ex =>
      ex.printStackTrace()
      sys.exit(1)
    }

  }
  catch {
    case ex: Throwable =>
      ex.printStackTrace()
      sys.exit(1)
  }

}
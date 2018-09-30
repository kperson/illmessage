package com.github.kperson.routing

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.github.kperson.app.{HttpAdapter, MessageACK, MessageSubscriptionSource}
import com.github.kperson.aws.dynamo.DynamoClient
import com.github.kperson.aws.s3.S3Client
import com.github.kperson.dao.AmazonSubscriptionDAO
import com.github.kperson.wal.{WAL, WALTransfer, WALTransferMessage}


object Main extends App {

  implicit val system = ActorSystem("app")
  implicit val materializer = ActorMaterializer()
  implicit val ec = materializer.executionContext

  try {
    val dynamoClient = new DynamoClient("us-east-1")
    val wal = new WAL(dynamoClient, "my_val")
    val walTransfer = new WALTransfer(wal)
    val s3Client = new S3Client("us-east-1")
    val subscriptionDAO = new AmazonSubscriptionDAO("vidicast.subscription", s3Client)

    val (orderingFlow, onMessageSent) = Multiplex.flow(new MessageACK(wal))


    val walMessages = wal.load().map { messages =>
      messages.map {  m =>
        new WALTransferMessage(
          m.message,
          m.messageId
        )
      }
    }

    //load WAL messages before starting HTTP server
    walMessages.foreach { wm =>
      wm.foreach { walTransfer.add(_) }
      MessageSubscriptionSource(subscriptionDAO, walTransfer)
        .via(orderingFlow)
        .runForeach { x =>
          onMessageSent(x.subscription.id, x.messageId)
        }
      val httpAdapter = new HttpAdapter(walTransfer, subscriptionDAO)
      httpAdapter.run()
    }

  }

  catch {
    case ex: Throwable =>
      println(ex)
      ex.printStackTrace()
      sys.exit(1)
  }

}
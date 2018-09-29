package com.github.kperson.routing

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.github.kperson.aws.s3.S3Client
import com.github.kperson.dao.AmazonSubscriptionDAO
import com.github.kperson.model.MessageSubscription

import scala.concurrent.Await
import scala.concurrent.duration._

object Main extends App {

  implicit val system = ActorSystem("app")
  implicit val materializer = ActorMaterializer()
  implicit val ec = materializer.executionContext

  try {

    val s3Client = new S3Client("us-east-1")
    val subscriptionDAO = new AmazonSubscriptionDAO("TODO", s3Client)
    val ms = MessageSubscription("e1", "b1", "q1", "a1")
    val f = subscriptionDAO.save(ms)
    Await.result(f, 5.seconds)
    sys.exit(0)
  }
  catch {
    case ex: Throwable =>
      println(ex)
      ex.printStackTrace()
      sys.exit(0)
  }



  //val api = new API()
  //api.run()

}
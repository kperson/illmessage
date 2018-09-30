package com.github.kperson.routing

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.github.kperson.api.API
import com.github.kperson.aws.s3.S3Client
import com.github.kperson.dao.AmazonSubscriptionDAO


object Main extends App {

  implicit val system = ActorSystem("app")
  implicit val materializer = ActorMaterializer()
  implicit val ec = materializer.executionContext

  try {
    val s3Client = new S3Client("us-east-1")
    val subscriptionDAO = new AmazonSubscriptionDAO("vidicast.subscription", s3Client)
    val api = new API(subscriptionDAO)
    api.run()
  }
    
  catch {
    case ex: Throwable =>
      println(ex)
      ex.printStackTrace()
      sys.exit(1)
  }

}
package com.github.kperson.background

import com.github.kperson.app.AppInit


object Main extends App with AppInit {

//  val logger = LoggerFactory.getLogger(getClass)
//
//  logger.info(s"background processor started with args = ${args.mkString(" ")}")
//
//  val redeliveryFut = args match {
//    case Redelivery(sub) =>
//      Redelivery(sub, deadLetterQueueDAO)
//    case _ => Future.successful(true)
//  }
//
//  val allFut = Future.sequence(
//    List(
//      redeliveryFut
//    )
//  )
//
//  allFut.onComplete { case rs =>
//    actorMaterializer.shutdown()
//    system.terminate()
//    rs match {
//      case Success(_) =>
//        System.exit(0)
//        logger.info("background processor succeeded")
//      case Failure(err) =>
//        logger.error("background processor failed", err)
//        System.exit(1)
//    }

}

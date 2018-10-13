package com.github.kperson.app

//import akka.stream.scaladsl.Source
//import com.github.kperson.dao.SubscriptionDAO
//import com.github.kperson.routing.{MessageSubscriptions, TopicMatching}
//import com.github.kperson.wal.{WALTransfer, WALTransferPublisher}
//
//import scala.concurrent.{ExecutionContext, Future}

//object MessageSubscriptionSource {
//
//  def apply(subscriptionDAO: SubscriptionDAO, walTransfer: WALTransfer)(implicit ec: ExecutionContext) = {
//    Source.fromPublisher(new WALTransferPublisher(walTransfer)).mapAsync(5) { case (optSubscription, messages) =>
//      optSubscription match {
//        case Some(subscription) =>
//          val m = messages.map { case (message, id) =>
//            MessageSubscriptions(message, List(subscription), id)
//          }
//          Future.successful(m)
//        case _ => {
//          for {
//            subscriptions <- subscriptionDAO.fetchAllSubscriptions()
//          } yield {
//            messages.map { case (message, id) =>
//              val matchingSubscriptions = subscriptions
//                .filter { s =>
//                  s.exchange == message.exchange
//                }.filter { s =>
//                TopicMatching.matchesTopicBinding(s.bindingKey, message.routingKey)
//              }
//              MessageSubscriptions(message, matchingSubscriptions, id)
//            }
//          }
//        }
//      }
//    }.mapConcat { x => x }
//  }
//
//}

package com.github.kperson.routing

import com.github.kperson.dao.DAO
import com.github.kperson.model.Message

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object Routing {

  private def serializeFutures[A](list: Iterable[A])(fn: A => Future[Unit]): Future[Unit] = {
    val zero = Future(())
    list.foldLeft(zero) { (accFuture, nextItem) =>
      accFuture.flatMap(_ => {
        val nextFuture = fn(nextItem)
        nextFuture
      })
    }
  }

  def handle(dao: DAO, messages: List[Message]): Future[Unit] = {
    //fetch all the subscriptions
    dao.fetchSubscriptions.flatMap { subscriptions =>
      serializeFutures(messages) { message =>
        //find all the matching subscriptions
        val matchSubscriptions = subscriptions.getOrElse(message.exchange, List.empty).filter { subscription =>
         TopicMatching.matchesTopicBinding(subscription.bindingKey, message.routingKey)
        }
        //attempt to deliver messages
        Future.sequence (
          matchSubscriptions.map { dao.deliverMessage(message, _) }
        ).map { _ => Unit }
      }
    }
  }

}

package com.github.kperson.api

import akka.http.scaladsl.model._
import akka.http.scaladsl.server

import akka.http.scaladsl.server.Directives._

import com.github.kperson.deadletter.DeadLetterQueueDAO
import com.github.kperson.model.MessageSubscription

import org.slf4j.Logger


trait DeadLetterAPI extends MarshallingSupport {

  def deadLetterQueueDAO: DeadLetterQueueDAO
  def logger: Logger

  val deadLetterRoute: server.Route = {
    pathPrefix("dead-letter") {
      post {
        decodeRequest {
          entity(as[MessageSubscription]) { subscription =>
            path("redeliver") {
              logger.info(s"redelivery requested for subscription = ${subscription.id}, queue = ${subscription.queue}, account id = ${subscription.accountId}")
              onSuccess(deadLetterQueueDAO.triggerRedeliver(subscription)) { _ =>
                complete(StatusCodes.Accepted)
              }
            }
          }
        }
      }
    }
  }

}

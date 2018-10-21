package com.github.kperson.api

import akka.http.scaladsl.server
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._

import com.github.kperson.model.MessageSubscription
import com.github.kperson.subscription.SubscriptionDAO


trait SubscriptionAPI extends MarshallingSupport {

  def subscriptionDAO: SubscriptionDAO

  val subscriptionRoute: server.Route = {
    path("subscription") {
      decodeRequest {
        entity(as[MessageSubscription]) { subscription =>
          post {
            onSuccess(subscriptionDAO.save(subscription)) { sub =>
              complete((StatusCodes.OK, sub))
            }
          } ~
          delete {
            onSuccess(subscriptionDAO.delete(subscription.exchange, subscription.id)) {
              case Some(sub) => complete((StatusCodes.OK, sub))
              case _ => complete((StatusCodes.NotFound, None))
            }
          }
        }
      }
    }
  }

}

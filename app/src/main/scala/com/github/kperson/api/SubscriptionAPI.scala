package com.github.kperson.api

import akka.http.scaladsl.server
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._

import com.github.kperson.model.MessageSubscription
import com.github.kperson.subscription.SubscriptionDAO


trait SubscriptionAPI extends MarshallingSupport {

  val subscriptionDAO: SubscriptionDAO

  val subscriptionRoute: server.Route = {
    pathPrefix("subscription") {
      pathPrefix("account-id" / Segment) { accountId =>
        pathPrefix("exchange" / Segment) { exchange =>
          pathPrefix("binding-key" / Segment) { bindingKey =>
            path("queue" / Segment) { queue =>
              val subscription = MessageSubscription(exchange, bindingKey, queue, accountId)
              post {
                onSuccess(subscriptionDAO.save(subscription)) { sub =>
                  complete((StatusCodes.OK, sub))
                }
              } ~
              delete {
                onSuccess(subscriptionDAO.delete(exchange, subscription.id)) {
                  case Some(sub) => complete((StatusCodes.OK, sub))
                  case _ => complete((StatusCodes.NotFound, None))
                }
              }
            }
          }
        }
      }
    }
  }


}

package com.github.kperson.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import com.github.kperson.subscription.SubscriptionDAO
import com.github.kperson.model.MessageSubscription

trait DeadLetterAPI extends MarshallingSupport {

  def subscriptionDAO: SubscriptionDAO

  def subscriptionRoute = {
    pathPrefix("dead-letter") {
      pathPrefix("account-id" / Segment) { accountId =>
        pathPrefix("exchange" / Segment) { exchange =>
          pathPrefix("binding-key" / Segment) { bindingKey =>
            pathPrefix("queue" / Segment) { queue =>
              val subscription = MessageSubscription(exchange, bindingKey, queue, accountId)
              path("size") {
                complete("hi")
              }
            }
          }
        }
      }
    }
  }

}

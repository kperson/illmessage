package com.github.kperson.subscription

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server
import akka.http.scaladsl.server.Directives._

import com.github.kperson.api.MarshallingSupport
import com.github.kperson.model.MessageSubscription


trait SubscriptionAPI extends MarshallingSupport {

  def subscriptionDAO: SubscriptionDAO

  val subscriptionRoute: server.Route = {
    pathPrefix("subscription") {
      pathEnd {
        decodeRequest {
          entity(as[MessageSubscription]) { subscription =>
            post {
              onSuccess(subscriptionDAO.save(subscription)) { sub =>
                complete((StatusCodes.OK, sub))
              }
            }
          }
        }
      } ~
      path(Segment / "exchange" / Segment) { (subscriptionId, exchangeId) =>
        delete {
          onSuccess(subscriptionDAO.delete(exchangeId, subscriptionId)) { _ =>
            complete((StatusCodes.NoContent, ""))
          }
        }
      }
    }
  }

}

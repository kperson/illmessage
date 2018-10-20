package com.github.kperson.api

import akka.http.scaladsl.model._
import akka.http.scaladsl.server
import akka.http.scaladsl.server.Directives._

import com.github.kperson.deadletter.DeadLetterQueueDAO
import com.github.kperson.model.MessageSubscription


trait DeadLetterAPI extends MarshallingSupport {

  def deadLetterQueueDAO: DeadLetterQueueDAO

  val deadLetterRoute: server.Route = {
    pathPrefix("dead-letter") {
      pathPrefix("account-id" / Segment) { accountId =>
        pathPrefix("exchange" / Segment) { exchange =>
          pathPrefix("binding-key" / Segment) { bindingKey =>
            pathPrefix("queue" / Segment) { queue =>
              val subscription = MessageSubscription(exchange, bindingKey, queue, accountId)
              path("redeliver") {
                post {
                  onSuccess(deadLetterQueueDAO.loadToWAL(subscription)) { _ =>
                    complete(StatusCodes.NoContent)
                  }
                }
              }
            }
          }
        }
      }
    }
  }

}

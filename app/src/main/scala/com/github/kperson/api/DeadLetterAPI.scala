package com.github.kperson.api

import akka.http.scaladsl.model._
import akka.http.scaladsl.server
import akka.http.scaladsl.server.Directives._

import com.github.kperson.deadletter.DeadLetterQueue
import com.github.kperson.model.MessageSubscription


trait DeadLetterAPI extends MarshallingSupport {

  def deadLetter: DeadLetterQueue

  val deadLetterRoute: server.Route = {
    pathPrefix("dead-letter") {
      pathPrefix("account-id" / Segment) { accountId =>
        pathPrefix("exchange" / Segment) { exchange =>
          pathPrefix("binding-key" / Segment) { bindingKey =>
            pathPrefix("queue" / Segment) { queue =>
              val subscription = MessageSubscription(exchange, bindingKey, queue, accountId)
              path("redeliver") {
                post {
                  onSuccess(deadLetter.loadToWAL(subscription)) { _ =>
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

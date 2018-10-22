package com.github.kperson.api

import akka.http.scaladsl.model._
import akka.http.scaladsl.server
import akka.http.scaladsl.server.Directives._
import com.github.kperson.deadletter.DeadLetterQueueDAO
import com.github.kperson.model.MessageSubscription
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success}


trait DeadLetterAPI extends MarshallingSupport {

  def deadLetterQueueDAO: DeadLetterQueueDAO

  private val logger = LoggerFactory.getLogger(getClass)

  val deadLetterRoute: server.Route = {
    pathPrefix("dead-letter") {
      pathPrefix("account-id" / Segment) { accountId =>
        pathPrefix("exchange" / Segment) { exchange =>
          pathPrefix("binding-key" / Segment) { bindingKey =>
            pathPrefix("queue" / Segment) { queue =>
              val subscription = MessageSubscription(exchange, bindingKey, queue, accountId)
              path("redeliver") {
                post {
                  onComplete(deadLetterQueueDAO.triggerRedeliver(subscription)) {
                    case Success(_) => complete(StatusCodes.NoContent)
                    case Failure(err) =>
                      logger.error("redeliver enqueue failed", err)
                      complete(StatusCodes.InternalServerError)
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

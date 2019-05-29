package com.github.kperson.delivery

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server
import akka.http.scaladsl.server.Directives._

import com.github.kperson.api.MarshallingSupport


trait DeliveryAPI extends MarshallingSupport {

  def deliveryDAO: DeliveryDAO

  val deliveryRoute: server.Route = {
    pathPrefix("ack") {
      pathEnd {
        decodeRequest {
          entity(as[List[AckRequest]]) { requests =>
            post {
              onSuccess(deliveryDAO.bulkAck(requests)) { _ =>
                complete(StatusCodes.NoContent)
              }
            }
          }
        }
      }
    }
  }

}

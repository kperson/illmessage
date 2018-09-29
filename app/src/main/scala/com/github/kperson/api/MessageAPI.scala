package com.github.kperson.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._

import com.github.kperson.model.Message


trait MessageAPI extends MarshallingSupport {

  def messageRoute = {
    path("message") {
      post {
        decodeRequest {
          entity(as[List[Message]]) { messages =>
            complete {
              (StatusCodes.OK, messages)
            }
          }
        }
      }
    }
  }

}

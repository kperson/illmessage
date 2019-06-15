package com.github.kperson.delivery

import com.github.kperson.lambda._
import com.github.kperson.serialization._

import scala.concurrent.ExecutionContext
import trail.Root


trait DeliveryAPI {

  def deliveryDAO: DeliveryDAO
  implicit val ec: ExecutionContext

  private val deliveryMatch = Root / "ack"

  val deliveryRoute: RequestHandler = {
    case (POST, deliveryMatch(_), req) =>
      val requests = readJSON[List[AckRequest]](req.bodyInputStream)
      deliveryDAO.bulkAck(requests).map { _ =>
        LambdaHttpResponse(204)
      }
  }

}
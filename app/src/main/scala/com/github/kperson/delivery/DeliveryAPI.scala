package com.github.kperson.delivery

import com.github.kperson.serialization.JSONFormats.formats
import com.github.kperson.lambda._

import org.json4s.jackson.Serialization._

import scala.concurrent.ExecutionContext

import trail.Root


trait DeliveryAPI {

  def deliveryDAO: DeliveryDAO
  implicit val ec: ExecutionContext

  private val deliveryMatch = Root / "ack"

  val deliveryRoute: RequestHandler = {
    case (POST, deliveryMatch(_), req) =>
      val requests = read[List[AckRequest]](req.bodyInputStream)
      deliveryDAO.bulkAck(requests).map { _ =>
        LambdaHttpResponse(204)
      }
  }

}
package com.github.kperson.subscription

import com.github.kperson.lambda._
import com.github.kperson.model.MessageSubscription
import com.github.kperson.serialization.JSONFormats.formats

import org.json4s.jackson.Serialization._

import scala.concurrent.ExecutionContext

import trail._


trait SubscriptionAPI {

  def subscriptionDAO: SubscriptionDAO

  implicit val ec: ExecutionContext


  private val subscriptionMatch = Root / "subscription"
  val subscriptionRoute: RequestHandler = {
    case (POST, subscriptionMatch(_), req) =>
      val subscription = read[MessageSubscription](req.bodyInputStream)
      subscriptionDAO.save(subscription).map { sub =>
        LambdaHttpResponse(200, write(sub), Map("Content-Type" -> "application/json"))
      }
  }

}
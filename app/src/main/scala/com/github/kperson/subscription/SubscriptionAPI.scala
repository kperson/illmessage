package com.github.kperson.subscription

import com.github.kperson.lambda._
import com.github.kperson.model.MessageSubscription

import com.github.kperson.serialization._

import scala.concurrent.ExecutionContext

import trail._


trait SubscriptionAPI {

  def subscriptionDAO: SubscriptionDAO

  implicit val ec: ExecutionContext


  private val subscriptionMatch = Root / "subscription"
  val subscriptionRoute: RequestHandler = {
    case (POST, subscriptionMatch(_), req) =>
      val subscription = readJSON[MessageSubscription](req.bodyInputStream)
      subscriptionDAO.save(subscription).map { sub =>
        LambdaHttpResponse(200, writeJSON(sub), Map("Content-Type" -> "application/json"))
      }
  }

}
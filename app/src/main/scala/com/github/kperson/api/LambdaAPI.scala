package com.github.kperson.api

import akka.http.scaladsl.server.Route

import com.github.kperson.app.AppInit
import com.github.kperson.lambda.LambdaAkkaAdapter

class LambdaAPI extends LambdaAkkaAdapter with AppInit {

  val api = new API(walDAO, subscriptionDAO, deliveryDAO)

  val route: Route = api.route

}

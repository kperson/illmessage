package com.github.kperson.api

import com.github.kperson.app.AppInit
import com.github.kperson.lambda.{LambdaAkkaAdapter, RequestHandler}


class LambdaAPI extends LambdaAkkaAdapter with AppInit {

  val api = new API(walDAO, subscriptionDAO, deliveryDAO)

  val route: RequestHandler = api.route

}

package com.github.kperson.app

import akka.http.scaladsl.server.Route
import com.github.kperson.lambda.LambdaAkkaAdapter

class LambdaInit extends LambdaAkkaAdapter with Init {

  val route: Route = api.route

}
package com.github.kperson.app

import akka.http.scaladsl.server.Route
import com.github.kperson.lambda.LambdaAkkaAdapter

class LambdaAPIInit extends LambdaAkkaAdapter with APIInit {

  val route: Route = api.route

}
package com.github.kperson.app

import akka.http.scaladsl.server
import akka.stream.ActorMaterializer

import com.github.kperson.lambda.LambdaAkkaAdapter


class LambdaInit extends LambdaAkkaAdapter {

  import Init._

  lazy val actorMaterializer: ActorMaterializer = materializer
  lazy val route: server.Route = api.route

}
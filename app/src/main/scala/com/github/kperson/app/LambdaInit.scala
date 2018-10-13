package com.github.kperson.app

import akka.http.scaladsl.server
import akka.stream.ActorMaterializer

import com.github.kperson.lambda.LambdaAkkaAdapter


class LambdaInit extends LambdaAkkaAdapter {

  lazy val actorMaterializer: ActorMaterializer = Init.materializer
  lazy val route: server.Route = Init.api.route

}
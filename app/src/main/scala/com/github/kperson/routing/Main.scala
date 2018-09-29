package com.github.kperson.routing

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import com.github.kperson.api.API


object Main extends App {

  implicit val system = ActorSystem("app")
  implicit val materializer = ActorMaterializer()

  val api = new API()
  api.run()

}
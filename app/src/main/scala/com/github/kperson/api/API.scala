package com.github.kperson.api

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.Materializer
import org.json4s.{Formats, NoTypeHints}
import org.json4s.jackson.Serialization


class API(
  implicit val fm: Materializer,
  val system: ActorSystem
)
extends MessageAPI
with SubscriptionAPI {

  lazy val jsonFormats: Formats = Serialization.formats(NoTypeHints)
  val route = messageRoute


  def run(interface: String = "0.0.0.0", port: Int = 8080) {
    Http().bindAndHandle(route, interface, port)
  }

}

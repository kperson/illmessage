package com.github.kperson.api

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.Materializer
import akka.http.scaladsl.server.Directives._

import com.github.kperson.dao.SubscriptionDAO
import com.github.kperson.serialization.JSONFormats

import org.json4s.Formats


class API(
  val subscriptionDAO: SubscriptionDAO
)(implicit fm: Materializer, system: ActorSystem)
extends MessageAPI
with SubscriptionAPI {

  lazy val jsonFormats: Formats = JSONFormats.formats
  val route = messageRoute ~ subscriptionRoute

  def run(interface: String = "0.0.0.0", port: Int = 8080) {
    Http().bindAndHandle(route, interface, port)
  }

}

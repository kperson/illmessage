package com.github.kperson.api

import akka.actor.ActorSystem
import akka.http.scaladsl.{server, Http}
import akka.stream.Materializer
import akka.http.scaladsl.server.Directives._

import com.github.kperson.deadletter.DeadLetterQueue
import com.github.kperson.subscription.SubscriptionDAO
import com.github.kperson.serialization.JSONFormats
import com.github.kperson.wal.WAL

import org.json4s.Formats


class API(
  val wal: WAL,
  val subscriptionDAO: SubscriptionDAO,
  val deadLetter: DeadLetterQueue
)(implicit fm: Materializer, system: ActorSystem)
extends MessageAPI
with SubscriptionAPI
with DeadLetterAPI {


  val jsonFormats: Formats = JSONFormats.formats
  val route: server.Route = messageRoute ~ subscriptionRoute ~ deadLetterRoute

  def run(interface: String = "0.0.0.0", port: Int = 8080) {
    Http().bindAndHandle(route, interface, port)
  }

}

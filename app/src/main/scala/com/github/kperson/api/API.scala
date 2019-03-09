package com.github.kperson.api

import akka.actor.ActorSystem
import akka.http.scaladsl.{Http, server}
import akka.stream.Materializer
import akka.http.scaladsl.server.Directives._

import com.github.kperson.subscription.{SubscriptionAPI, SubscriptionDAO}
import com.github.kperson.serialization.JSONFormats
import com.github.kperson.wal.{WriteAheadAPI, WriteAheadDAO}

import org.json4s.Formats
import org.slf4j.LoggerFactory


class API(
  val writeAheadDAO: WriteAheadDAO,
  val subscriptionDAO: SubscriptionDAO,
)(implicit fm: Materializer, system: ActorSystem)
extends WriteAheadAPI
with SubscriptionAPI
 {

  val logger = LoggerFactory.getLogger(getClass)
  val jsonFormats: Formats = JSONFormats.formats
  val route: server.Route = writeAheadRoute ~ subscriptionRoute

  def run(interface: String = "0.0.0.0", port: Int = 8080) {
    Http().bindAndHandle(route, interface, port)
  }

}

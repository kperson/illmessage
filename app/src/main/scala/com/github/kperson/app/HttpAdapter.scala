package com.github.kperson.app

import akka.actor.ActorSystem
import akka.stream.Materializer

import com.github.kperson.api.API
import com.github.kperson.dao.SubscriptionDAO
import com.github.kperson.wal.WAL


class HttpAdapter(
  val wal: WAL,
  val subscriptionDAO: SubscriptionDAO
)(implicit fm: Materializer, system: ActorSystem) {

  def run(port: Int = 8080) {
    val api = new API(wal, subscriptionDAO)
    api.run(port = port)
  }

}

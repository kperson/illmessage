package com.github.kperson.app

import akka.actor.ActorSystem
import akka.stream.Materializer

import com.github.kperson.api.API
import com.github.kperson.dao.SubscriptionDAO
import com.github.kperson.wal.WALTransfer


class HttpAdapter(
  val walTransfer: WALTransfer,
  val subscriptionDAO: SubscriptionDAO
)(implicit fm: Materializer, system: ActorSystem) {

  def run() {
    val api = new API(walTransfer, subscriptionDAO)
    api.run()
  }

}

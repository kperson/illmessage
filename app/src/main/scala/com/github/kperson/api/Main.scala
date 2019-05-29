package com.github.kperson.api

import com.github.kperson.app.AppInit


object Main extends App with AppInit {

  val api = new API(walDAO, subscriptionDAO, deliveryDAO)
  api.run(port = config.port)

}
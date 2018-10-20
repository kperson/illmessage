package com.github.kperson.app

import com.github.kperson.api.API

object Main extends App with AppInit {

  val api = new API(walDAO, subscriptionDAO, deadLetterQueueDAO)

  api.run(port = config.port)

}
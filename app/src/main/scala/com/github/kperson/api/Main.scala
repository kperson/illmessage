package com.github.kperson.api

import com.github.kperson.app.AppInit


object Main extends App with AppInit {


  import com.github.kperson.serialization.JSONFormats.formats

  val x = lambdaClient.invoke(config.deadLetterLambda, Map("hello" -> "world"))

  val api = new API(walDAO, subscriptionDAO, deadLetterQueueDAO)

  api.run(port = config.port)

}
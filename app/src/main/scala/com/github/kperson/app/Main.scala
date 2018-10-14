package com.github.kperson.app

import io.lemonlabs.uri.QueryString


object Main extends App with Init {



  println()

  api.run(port = config.port)

}
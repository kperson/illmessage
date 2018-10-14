package com.github.kperson.app


object Main extends App with Init {

  api.run(port = config.port)

}
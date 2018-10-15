package com.github.kperson.app


object Main extends App with APIInit {

  api.run(port = config.port)

}
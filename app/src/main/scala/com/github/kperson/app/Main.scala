package com.github.kperson.app


object Main extends App {

  import Init._

  api.run(port = config.port)

}
package com.github.kperson.app

import com.github.kperson.api.APIInit


object Main extends App with APIInit {

  api.run(port = config.port)

}
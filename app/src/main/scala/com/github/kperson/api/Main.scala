package com.github.kperson.api

import com.github.kperson.app.AppInit


object Main extends App with AppInit {

  api.run(port = config.port)

}
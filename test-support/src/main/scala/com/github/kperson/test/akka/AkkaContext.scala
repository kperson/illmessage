package com.github.kperson.test.akka

import java.util.UUID

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

trait AkkaContext {

  def withAkka(testCode: (ActorMaterializer) => Any) {
    implicit val system = ActorSystem(UUID.randomUUID().toString)
    implicit val materializer = ActorMaterializer()
    testCode(materializer)
    materializer.shutdown()
    system.terminate()
  }

}

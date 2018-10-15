package com.github.kperson.app

import com.github.kperson.aws.dynamo.{ChangeCapture, DynamoMap, StreamChangeCaptureHandler}

class MessageProcessor extends StreamChangeCaptureHandler {

  def handleChange(change: ChangeCapture[DynamoMap]) {
    println("change")
    println(change)
  }

}

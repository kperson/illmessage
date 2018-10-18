package com.github.kperson.app

import com.github.kperson.aws.dynamo.{ChangeCapture, DynamoMap, New, StreamChangeCaptureHandler}
import org.slf4j.LoggerFactory

class MessageProcessor extends StreamChangeCaptureHandler {

  val logger = LoggerFactory.getLogger(getClass)

  def handleChange(change: ChangeCapture[DynamoMap]) {
    logger.debug(s"processing change, $change")
    change.map { _.flatten } match {
      case New(_, item) =>
        logger.info(s"received new payload, $item")
        println(item)
      case _ =>
    }
  }

}

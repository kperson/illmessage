package com.github.kperson.app

import com.github.kperson.aws.dynamo._
import com.github.kperson.serialization.JSONFormats
import com.github.kperson.wal.WALRecord
import org.json4s.jackson.Serialization._

import org.slf4j.LoggerFactory


class MessageProcessor extends StreamChangeCaptureHandler {

  implicit val formats = JSONFormats.formats

  val logger = LoggerFactory.getLogger(getClass)

  def handleChange(change: ChangeCapture[DynamoMap]) {
    logger.debug(s"processing change, $change")
    change.map { _.flatten } match {
      case New(_, item) =>
        val record = read[WALRecord](write(item))
        logger.info(s"received new record, $record")
        println(item)
      case _ =>
    }
  }

}

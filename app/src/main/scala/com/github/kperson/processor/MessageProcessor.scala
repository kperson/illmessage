package com.github.kperson.processor

import com.github.kperson.aws.dynamo._
import com.github.kperson.dao.SubscriptionDAO
import com.github.kperson.serialization.JSONFormats
import com.github.kperson.wal.WALRecord
import org.json4s.jackson.Serialization._
import org.slf4j.LoggerFactory

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

trait MessageProcessorDependencies {

  def subscriptionDAO: SubscriptionDAO
  def removeWALRecord(record: WALRecord): Future[Any]

}


trait MessageProcessor extends StreamChangeCaptureHandler with MessageProcessorDependencies {

  implicit val formats = JSONFormats.formats


  val logger = LoggerFactory.getLogger(getClass)

  def handleChange(change: ChangeCapture[DynamoMap]) {
    logger.debug(s"processing change, $change")
    change.map { _.flatten } match {
      case New(_, item) =>
        val record = read[WALRecord](write(item))
        logger.info(s"received new record, $record")
        val f = removeWALRecord(record)
        Await.result(f, 10.seconds)
      case _ =>
        logger.debug("ignoring change")
    }
  }

}

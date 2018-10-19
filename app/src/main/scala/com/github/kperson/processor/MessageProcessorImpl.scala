package com.github.kperson.processor

import com.github.kperson.api.APIInit
import com.github.kperson.aws.sqs.SQSClient
import com.github.kperson.deadletter.{DeadLetterMessage, DeadLetterQueue}
import com.github.kperson.model.MessageSubscription
import com.github.kperson.wal.WALRecord

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._


class MessageProcessorImpl extends MessageProcessor with APIInit {

  val sqsClient = new SQSClient(config.awsRegion, "NA")
  val deadLetterQueue = new DeadLetterQueue(dynamoClient, config.deadLetterTable, wal)

  def removeWALRecord(record: WALRecord): Future[Any] = {
    wal.remove(record.messageId, record.message.partitionKey)
  }

  def subscriptions: Future[List[MessageSubscription]] = subscriptionDAO.fetchAllSubscriptions()

  def sendMessage(
    queueName: String,
    messageBody: String,
    delay: Option[FiniteDuration],
    messageDeduplicationId: Option[String],
    messageGroupId: Option[String],
    messageAccountId: Option[String]
  ): Future[Any] = {
    sqsClient.sendMessage(queueName, messageBody, delay, messageDeduplicationId, messageGroupId, messageAccountId)
  }

  def saveDeadLetter(record: WALRecord, subscription: MessageSubscription, reason: String): Future[Any] = {
    val dlm = DeadLetterMessage(
      subscription,
      record.messageId,
      record.message,
      System.currentTimeMillis(),
      System.currentTimeMillis() / 1000L + 14.days.toSeconds,
      reason
    )
    deadLetterQueue.write(dlm)
  }



}

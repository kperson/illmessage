package com.github.kperson.aws.sqs

import com.amazonaws.auth.AWSCredentialsProvider
import com.github.kperson.aws.{Credentials, HttpRequest}

import org.reactivestreams.Publisher

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.xml.XML


case class SNSAttribute(name: String, value: String)
case class SNSMessage[T](id: String, receiptHandle: String, body: T, attributes: List[SNSAttribute]) {

  def map[Q](f: (T) => Q): SNSMessage[Q] = {
    SNSMessage(id, receiptHandle, f(body), attributes)
  }

}

class SQSQueueClient(
  region: String,
  accountId: String,
  credentialsProvider: AWSCredentialsProvider = Credentials.defaultCredentialsProvider
)(implicit ec: ExecutionContext) {

  private def createRequest(method: String, path: String = "", queryParams: Map[String, String]): HttpRequest = {
    val params = queryParams.map { case (k, v) => (k, Some(v)) }
    new HttpRequest(credentialsProvider.getCredentials, "sqs", region, s"https://sqs.$region.amazonaws.com", method = method, queryParams = params, path = path)
  }

  def createQueue(
     queueName: String,
     isFifo: Boolean = false,
     delay: FiniteDuration = 0.seconds,
     maximumMessageSizeBytes: Option[Int] = None,
     messageRetentionPeriod: Option[FiniteDuration] = None,
     policy: Option[String] = None,
     receiveMessageWaitTime: Option[FiniteDuration] = None,
     redrivePolicy: Option[String] = None,
     visibilityTimeout: Option[FiniteDuration] = None,
     contentBasedDeduplicationEnabled: Option[Boolean] = None
   ): Future[Any] = {

    val extras = Map(
      "FifoQueue" -> (if(isFifo) Some("true") else None),
      "DelaySeconds" -> Some(delay.toSeconds.toString),
      "MaximumMessageSize" -> maximumMessageSizeBytes.map { _.toString },
      "MessageRetentionPeriod" -> messageRetentionPeriod.map { _.toSeconds.toString },
      "Policy" -> policy,
      "ReceiveMessageWaitTimeSeconds" -> receiveMessageWaitTime.map { _.toSeconds.toString },
      "RedrivePolicy" -> redrivePolicy,
      "VisibilityTimeout" -> visibilityTimeout.map { _.toSeconds.toString },
      "ContentBasedDeduplication" -> contentBasedDeduplicationEnabled.map { _.toString }
    ).collect {
      case (k, Some(v)) => (k, v)
    }.toSeq

    val extraParams = extras.zip(1 to extras.length).flatMap { case ((k, v), index) =>
      Map(
        s"Attribute.$index.Name" -> k,
        s"Attribute.$index.Value" -> v
      )
    }.toMap

    createRequest("POST",  "", Map("Action" -> "CreateQueue", "QueueName" -> queueName) ++ extraParams).run()
  }

  def sendMessage(
    queueName: String,
    messageBody: String,
    delay: FiniteDuration = 0.seconds,
    messageDeduplicationId: Option[String] = None,
    messageGroupId: Option[String] = None
  ): Future[Any] = {
    val baseParams = Map(
      "DelaySeconds" -> Some(delay.toSeconds.toString),
      "MessageBody" -> Some(messageBody),
      "MessageDeduplicationId" -> messageDeduplicationId,
      "MessageGroupId" -> messageGroupId,
      "Action" -> Some("SendMessage")
    ).collect {
      case (k, Some(v)) => (k, v)
    }
    createRequest("POST",  s"$accountId/$queueName/", baseParams).run()
  }

  def fetchMessages (
    queueName: String,
    waitTime: Option[FiniteDuration] = None,
    visibilityTimeout: Option[FiniteDuration] = None,
    maxNumberOfMessages: Int = 1
  ): Future[List[SNSMessage[String]]] = {
    val baseParams = Map(
      "AttributeName" -> Some("All"),
      "VisibilityTimeout" -> visibilityTimeout.map { _.toSeconds.toString },
      "MaxNumberOfMessages" -> Some(maxNumberOfMessages.toString),
      "Action" -> Some("ReceiveMessage"),
      "WaitTimeSeconds" -> waitTime.map { _.toSeconds.toString }
    ).collect {
      case (k, Some(v)) => (k, v)
    }
    createRequest("POST",  s"$accountId/$queueName/", baseParams).run(waitTime.getOrElse(1.seconds) + 3.second).map { res =>
      val xml = XML.loadString(new String(res.body))
      (xml \\ "ReceiveMessageResult" \\ "Message").map {  m =>
        val messageId = (m \ "MessageId").text
        val body = (m \ "Body").text
        val receiptHandle = (m \ "ReceiptHandle").text
        val attributes = (m \\ "Attribute").map { a =>
          SNSAttribute( (a \ "Name").text, (a \ "Value").text)
        }
        SNSMessage(messageId, receiptHandle, body, attributes.toList)
      }.toList
    }
  }

  def deleteMessage(
    queueName: String,
    receiptHandle: String
  ): Future[Any] = {
    val baseParams = Map(
      "ReceiptHandle" -> receiptHandle,
      "Action" -> "DeleteMessage"
    )
    createRequest("POST",  s"$accountId/$queueName/", baseParams).run()
  }

  def deleteQueue(queueName: String) {
    val baseParams = Map(
      "Action" -> "DeleteQueue"
    )
    createRequest("POST",  s"$accountId/$queueName/", baseParams).run()
  }

  def publisher(queueName: String, autoDelete: Boolean = false, backOffStrategy: Option[BackOffStrategy] = None): Publisher[SNSMessage[String]] = {
    new SQSPublisher(this, queueName, autoDelete, backOffStrategy)
  }

}

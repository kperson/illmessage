package com.github.kperson.aws.sqs

import java.util.UUID

import com.amazonaws.auth.AWSCredentialsProvider
import com.github.kperson.aws.{AWSError, AWSHttpResponse, Credentials, HttpRequest}
import org.reactivestreams.Publisher

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Try}
import scala.xml.XML


case class SNSAttribute(name: String, value: String)
case class SNSMessage[T](id: String, receiptHandle: String, body: T, attributes: List[SNSAttribute]) {

  def map[Q](f: (T) => Q): SNSMessage[Q] = {
    SNSMessage(id, receiptHandle, f(body), attributes)
  }

}

case class BatchFailure(code: String, message: String)
case class BatchSendException(failures: List[BatchFailure], response: AWSHttpResponse) extends RuntimeException(new String(response.body))

class SQSClient(
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

  def sendMessages(
    queueName: String,
    payloads: List[(String, Option[FiniteDuration], Option[String], Option[String])],
    messageAccountId: Option[String] = None,
  ): Future[Any] = {
    val uuid = UUID.randomUUID().toString.replace("-", "").take(10)
    val baseParams = (1 to payloads.length).zip(payloads).flatMap { case (index, (messageBody, duration, messageGroupId, messageDeduplicationId)) =>
      Map(
        s"SendMessageBatchRequestEntry.$index.Id" -> Some(s"$uuid-$index"),
        s"SendMessageBatchRequestEntry.$index.MessageBody" -> Some(messageBody),
        s"SendMessageBatchRequestEntry.$index.MessageGroupId" -> (if(queueName.endsWith(".fifo")) messageGroupId else None),
        s"SendMessageBatchRequestEntry.$index.MessageDeduplicationId" -> (if(queueName.endsWith(".fifo")) messageDeduplicationId else None),
        s"SendMessageBatchRequestEntry.$index.DelaySeconds" -> duration.map { _.toSeconds.toString }
      ).collect {
        case (k, Some(v)) => (k, v)
      }.toList
    }
    val params = baseParams.toMap + ("Action" -> "SendMessageBatch", "Version" -> "2012-11-05")
    createRequest("POST",  s"${messageAccountId.getOrElse(accountId)}/$queueName/", params).run()
      .recover {
        case  AWSError(resp) =>
          val xml = XML.loadString(new String(resp.body))
          println(xml)
          val failures = (xml \\ "Error").map { e =>
            val code = (e \ "Code").text
            val message = (e \ "Message").text
            BatchFailure(code, message)
          }
          Failure(BatchSendException(failures.toList, resp))
      }
  }


  def sendMessage(
    queueName: String,
    messageBody: String,
    delay: Option[FiniteDuration] = None,
    messageDeduplicationId: Option[String] = None,
    messageGroupId: Option[String] = None,
    messageAccountId: Option[String] = None
  ): Future[Any] = {
    val baseParams = Map(
      "DelaySeconds" -> delay.map { _.toSeconds.toString },
      "MessageBody" -> Some(messageBody),
      "MessageDeduplicationId" -> messageDeduplicationId,
      "MessageGroupId" -> messageGroupId,
      "Action" -> Some("SendMessage")
    ).collect {
      case (k, Some(v)) => (k, v)
    }
    createRequest("POST",  s"${messageAccountId.getOrElse(accountId)}/$queueName/", baseParams).run()
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

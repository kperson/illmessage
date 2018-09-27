package com.github.kperson.aws.sns

import com.amazonaws.auth.AWSCredentialsProvider

import com.github.kperson.aws.{Credentials, HttpRequest}

import scala.concurrent.Future

case class MessageAttributes(
  strings: Map[String, String] = Map.empty,
  integers: Map[String, Int] = Map.empty,
  doubles: Map[String, Double] = Map.empty
) {

  def params: Map[String, String] = {
    var next = 1
    val s = strings.zip(next until next + strings.size).map { case ((k, v), index) =>
      Map(
        s"MessageAttribute.$index.Name" -> k,
        s"MessageAttribute.$index.Value.StringValue" -> v,
        s"MessageAttribute.$index.Value.DataType" -> "String"
      )
    }.flatten
    next = next + strings.size
    val i = integers.zip(next until next + integers.size).map { case ((k, v), index) =>
      Map(
        s"MessageAttribute.$index.Name" -> k,
        s"MessageAttribute.$index.Value.StringValue" -> v.toString,
        s"MessageAttribute.$index.Value.DataType" -> "Number"
      )
    }.flatten
    next = next + doubles.size
    val d = doubles.zip(next until next + doubles.size).map { case ((k, v), index) =>
      Map(
        s"MessageAttribute.$index.Name" -> k,
        s"MessageAttribute.$index.Value.StringValue" -> v.toString,
        s"MessageAttribute.$index.Value.DataType" -> "Number"
      )
    }.flatten
    (s ++ i ++ d).toMap
  }

}

class SNSClient(
  region: String,
  credentialsProvider: AWSCredentialsProvider = Credentials.defaultCredentialsProvider
) {

  def publish(
   message: String,
   topicArn: String,
   subject: Option[String] = None,
   messageAttributes: MessageAttributes = MessageAttributes()
 ): Future[Any] = {
    val params = Map(
      "Message" -> Some(message),
      "TargetArn" -> Some(topicArn),
      "Subject" -> subject,
      "Action" -> Some("Publish")
    )
    val bodyParams = params.collect {
      case (k, Some(v)) => (k, v)
    }
    val finalParams = bodyParams ++ messageAttributes.params
    val optParams = finalParams.map { case (k, v) => (k, Some(v)) }
    request(optParams)
  }

  def request(params: Map[String, Option[String]]): Future[Any] = {
    new HttpRequest(credentialsProvider.getCredentials, "sns", region, s"https://sns.$region.amazonaws.com", method = "POST", queryParams = params).run()
  }

}

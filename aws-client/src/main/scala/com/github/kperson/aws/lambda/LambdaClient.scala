package com.github.kperson.aws.lambda

import java.nio.charset.StandardCharsets

import com.amazonaws.auth.AWSCredentialsProvider
import com.github.kperson.aws.{Credentials, HttpRequest}
import org.json4s.Formats
import org.json4s.jackson.Serialization.write

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

sealed trait InvocationType

object RequestResponse extends InvocationType {

  override def toString: String = "RequestResponse"

}
object Event extends InvocationType {

  override def toString: String = "Event"

}
object DryRun extends InvocationType {

  override def toString: String = "DryRun"

}

class LambdaClient(
  region: String,
  credentialsProvider: AWSCredentialsProvider = Credentials.defaultCredentialsProvider
)(implicit val ec: ExecutionContext) {

  val endpoint = s"https://lambda.${region}.amazonaws.com"

  def invoke[A <: AnyRef](
   function: String,
   payload: A,
   invocationType: InvocationType = RequestResponse,
   qualifier: Option[String] = None,
   timeout: FiniteDuration = 10.seconds
 )(implicit formats: Formats): Future[Array[Byte]] = {
    val payloadBytes = write(payload)(formats).getBytes(StandardCharsets.UTF_8)
    request(
      "POST",
      s"/2015-03-31/functions/$function/invocations",
      headers = Map("X-Amz-Invocation-Type" -> invocationType.toString),
      queryParams = qualifier.map { x => Map("Qualifier" -> x) }.getOrElse(Map.empty),
      payload = payloadBytes
    ).run(timeout).map { _.body }
  }

  private def request(
    method: String,
    path: String,
    payload: Array[Byte] = Array.emptyByteArray,
    headers: Map[String, String] = Map.empty,
    queryParams: Map[String, String] = Map.empty
 ): HttpRequest = {
    new HttpRequest(
      credentialsProvider.getCredentials,
      "lambda",
      region,
      endpoint,
      path,
      method = method,
      headers = headers,
      payload = payload,
      queryParams = queryParams.map { case (k, v) => (k, Some(v)) }
    )
  }

}

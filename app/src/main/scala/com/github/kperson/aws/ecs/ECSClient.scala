package com.github.kperson.aws.ecs

import java.nio.charset.StandardCharsets

import com.amazonaws.auth.AWSCredentialsProvider
import com.github.kperson.aws.{Credentials, HttpRequest}
import org.json4s.Formats
import org.json4s.jackson.Serialization._

import scala.concurrent.ExecutionContext


class ECSClient(
  region: String,
  credentialsProvider: AWSCredentialsProvider = Credentials.defaultCredentialsProvider
)(implicit ec: ExecutionContext) {

  def request[A <: AnyRef](
    payload: A,
    xAmzTarget: String
  )(implicit formats: Formats): HttpRequest = {
    new HttpRequest(
      credentialsProvider.getCredentials,
      "ecs",
      region,
      s"https://ecs.$region.amazonaws.com",
      method = "POST",
      headers = Map(
        "Content-Type" -> "application/x-amz-json-1.1",
        "X-Amz-Target" -> xAmzTarget
      ),
      payload = write(payload).getBytes(StandardCharsets.UTF_8)
    )
  }

}

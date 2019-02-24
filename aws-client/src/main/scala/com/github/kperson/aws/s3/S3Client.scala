package com.github.kperson.aws.s3


import com.amazonaws.auth.AWSCredentialsProvider
import com.github.kperson.aws.{Credentials, HttpRequest}

import scala.concurrent.Future

class S3Client(
  val region: String,
  credentialsProvider: AWSCredentialsProvider = Credentials.defaultCredentialsProvider
) extends ObjectOps {


  def request(
    method: String,
    path: String,
    payload: Array[Byte] = Array.empty,
    headers: Map[String, String] = Map.empty,
    queryParams: Map[String, Option[String]] = Map.empty
   ): HttpRequest = {
    new HttpRequest(
      credentialsProvider.getCredentials,
      "s3",
      region,
      s"https://s3.amazonaws.com",
      path = path,
      method = method,
      headers = headers,
      payload = payload,
      queryParams = queryParams
    )
  }

}

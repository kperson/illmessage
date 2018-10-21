package com.github.kperson.aws

import com.amazonaws.auth.AWSCredentials

import org.asynchttpclient.{AsyncHttpClient, Dsl, RequestBuilder}

import scala.concurrent.Future
import scala.concurrent.duration._

import AWSHttp._


object HttpRequest {

  val httpClient: AsyncHttpClient = Dsl.asyncHttpClient()

}


class HttpRequest(
  credentials: AWSCredentials,
  awsService: String,
  region: String,
  serviceEndpoint: String,
  path: String = "",
  method: String,
  headers: Map[String, String] = Map.empty,
  queryParams: Map[String, Option[String]] = Map.empty,
  payload: Array[Byte] = Array.empty
) {

  private val finalMethod = method.toUpperCase
  private val finalPath = if (path.startsWith("/")) path else "/" + path
  private val finalServiceEndpoint = if (serviceEndpoint.endsWith("/")) serviceEndpoint.substring(0, serviceEndpoint.length - 1) else serviceEndpoint

  private val builder = new RequestBuilder(finalMethod, true)
  builder.setUrl(s"$serviceEndpoint${AWSSigning.uriEncode(finalPath, isParameter = false)}")

  private val signing = AWSSigning(
    awsService,
    credentials,
    region,
    finalMethod,
    finalPath,
    Map(
      "Host" -> finalServiceEndpoint.replace("https://", "").replace("http://", "")
    ) ++ headers,
    queryParams,
    payload
  )

  def run(timeout: FiniteDuration = 10.seconds): Future[AWSHttpResponse] = {
    HttpRequest.httpClient.future(builder, signing, timeout)
  }


}

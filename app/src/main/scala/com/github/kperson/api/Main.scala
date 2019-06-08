package com.github.kperson.api

import com.amazonaws.services.lambda.runtime.RequestStreamHandler
import com.github.kperson.aws.{AWSHttp, AWSHttpResponse}
import java.io.{ByteArrayInputStream, ByteArrayOutputStream, PrintWriter, StringWriter}
import java.net.URI
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.{HttpClient, HttpRequest}
import java.nio.charset.StandardCharsets

import org.json4s.jackson.Serialization._
import org.json4s.NoTypeHints
import org.json4s.jackson.Serialization

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import AWSHttp._


object  Main {

  private val client = HttpClient.newHttpClient()

  private val handlers: Map[String, RequestStreamHandler] = Map(
    "com.github.kperson.api.TestHandler" -> new TestHandler()
  )

  def main(args: Array[String]) {
    val runtimeApiEndpoint = System.getenv("AWS_LAMBDA_RUNTIME_API")
    val handler = handlers(System.getenv("_HANDLER"))

    run(runtimeApiEndpoint, handler)
  }


  def runRequest(method: String, url: String, body: Array[Byte] = Array.emptyByteArray): Future[AWSHttpResponse] = {
    val builder = HttpRequest.newBuilder(new URI(url))
    builder.method(method, BodyPublishers.ofByteArray(body)).timeout(java.time.Duration.ofSeconds(61))
    client.future(builder.build())
  }

  def run(runtimeApiEndpoint: String, handler: RequestStreamHandler) {
    val jobFetchFuture = runRequest("GET", s"http://$runtimeApiEndpoint/2018-06-01/runtime/invocation/next")
    val jobFetch = Await.result(jobFetchFuture, 30.seconds)
    val requestId = jobFetch.lowerHeaders("Lambda-Runtime-Aws-Request-Id".toLowerCase)
    try {
      val out = new ByteArrayOutputStream()
      handler.handleRequest(new ByteArrayInputStream(jobFetch.body), out, null)
      Await.result(runRequest(
        "POST",
        s"http://$runtimeApiEndpoint/2018-06-01/runtime/invocation/$requestId/response",
        out.toByteArray
      ), 60.seconds)
      run(runtimeApiEndpoint, handler)
    }
    catch {
      case ex: Throwable =>
        val sw = new StringWriter()
        val pw = new PrintWriter(sw)
        val errorPayload = Map(
          "errorMessage" -> ex.getMessage,
          "localizedMessage" -> ex.getMessage,
          "stackTrace" -> ex.printStackTrace(pw)
        )
        val errorMessage = write(errorPayload)(Serialization.formats(NoTypeHints))
        System.err.println(errorMessage)
        Await.result(
          runRequest(
            "POST",
            s"http://$runtimeApiEndpoint/2018-06-01/runtime/invocation/$requestId/error",
            errorMessage.getBytes(StandardCharsets.UTF_8)
          ),
          60.seconds
        )
        run(runtimeApiEndpoint, handler)
    }
  }

}

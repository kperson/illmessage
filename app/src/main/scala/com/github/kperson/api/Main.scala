package com.github.kperson.api

import com.amazonaws.services.lambda.runtime.RequestStreamHandler
import com.github.kperson.aws.{AWSHttp, AWSHttpResponse}
import com.github.kperson.serialization._
import java.io._
import java.net.URI
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.{HttpClient, HttpRequest}
import java.nio.charset.StandardCharsets

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import AWSHttp._

object  Main {

  private val client = HttpClient.newHttpClient()


  def main(args: Array[String]) {
    val runtimeApiEndpoint = System.getenv("AWS_LAMBDA_RUNTIME_API")
    val clazz = Class.forName(System.getenv("_HANDLER"))
    val handler = clazz.getDeclaredConstructor().newInstance().asInstanceOf[RequestStreamHandler]
    run(runtimeApiEndpoint, handler)
  }

  def runRequest(method: String, url: String, body: Array[Byte] = Array.emptyByteArray): Future[AWSHttpResponse[Array[Byte]]] = {
    val builder = HttpRequest.newBuilder(new URI(url))
    builder.method(method, BodyPublishers.ofByteArray(body)).timeout(java.time.Duration.ofSeconds(25))
    client.future(builder.build())
  }

  def runRequestInputStream(method: String, url: String, body: Array[Byte] = Array.emptyByteArray): Future[AWSHttpResponse[InputStream]] = {
    val builder = HttpRequest.newBuilder(new URI(url))
    builder.method(method, BodyPublishers.ofByteArray(body)).timeout(java.time.Duration.ofSeconds(25))
    client.futureInputStream(builder.build())
  }

  def run(runtimeApiEndpoint: String, handler: RequestStreamHandler): Unit = {
    try {
      val jobFetchFuture = runRequestInputStream("GET", s"http://$runtimeApiEndpoint/2018-06-01/runtime/invocation/next")
      val is = Await.result(jobFetchFuture, 30.seconds)
      val requestId = is.lowerHeaders("Lambda-Runtime-Aws-Request-Id".toLowerCase)
      runCycle(runtimeApiEndpoint, handler, is.body, requestId)
    }
    catch {
      case _: Throwable => run(runtimeApiEndpoint, handler)
    }
  }

  def runCycle(runtimeApiEndpoint: String, handler: RequestStreamHandler, is: InputStream, requestId: String) {
    try {
      val out = new ByteArrayOutputStream()
      handler.handleRequest(is, out, null)
      Await.result(runRequest(
        "POST",
        s"http://$runtimeApiEndpoint/2018-06-01/runtime/invocation/$requestId/response",
        out.toByteArray
      ), 30.seconds)
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

        val errorMessage = writeJSON(errorPayload)
        System.err.println(errorMessage)
        Await.result(
          runRequest(
            "POST",
            s"http://$runtimeApiEndpoint/2018-06-01/runtime/invocation/$requestId/error",
            errorMessage.getBytes(StandardCharsets.UTF_8)
          ),
          30.seconds
        )
        run(runtimeApiEndpoint, handler)
    }
  }

}

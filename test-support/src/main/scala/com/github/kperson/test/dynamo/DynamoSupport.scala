package com.github.kperson.test.dynamo


import com.github.kperson.aws.dynamo.DynamoClient

import scala.concurrent.ExecutionContext.Implicits.global
import java.nio.charset.StandardCharsets

import org.json4s.{Formats, NoTypeHints}
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.io.Source
import scala.util.Random


trait DynamoSupport {

  val portRandom = new Random()

  def withDynamo(testCode: (String, String, DynamoClient) => Any) {
    val port = portRandom.nextInt(65535 - 49152) + 49152
    val props = System.getProperties()
    props.setProperty("aws.accessKeyId", "MY_TEST_ACCESS_KEY_ID")
    props.setProperty("aws.secretKey", "MY_TEST_SECRET_KEY")

    val containerName = s"dynamo_local_$port"
    val runArgs = Array("docker", "run", "-d", "--name", containerName, "-p", s"$port:8000", "amazon/dynamodb-local")
    val runProcess = Runtime.getRuntime().exec(runArgs)
    runProcess.waitFor()

    Thread.sleep(3200)

    try {
      val endpoint = s"http://localhost:${port}"
      val region = "us-west-1"
      val client = new DynamoClient(region, Some(endpoint))
      createTables(client)
      testCode(endpoint, region, client)
    }
    catch {
      case ex: Throwable => throw(ex)
    }
    finally {
      val args = Array("docker", "rm", "-f", containerName)
      val stopProcess = Runtime.getRuntime().exec(args)
      stopProcess.waitFor()
    }
  }

  def createTables(client: DynamoClient) {
    val json = Source.fromURL(getClass.getResource("/fixtures/tables.json"))
    implicit val formats: Formats = Serialization.formats(NoTypeHints)
    val tables = read[List[Map[String, Any]]](json.mkString)

    val creations = Future.sequence(tables.map { t =>
      val tableDef = write(t)
      client.request(tableDef.getBytes(StandardCharsets.UTF_8), "DynamoDB_20120810.CreateTable").run()
    })
    Await.result(creations, 10.seconds)
  }

}

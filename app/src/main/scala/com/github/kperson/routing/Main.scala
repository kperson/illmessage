package com.github.kperson.routing

import com.github.kperson.aws.dynamo.DynamoClient
import com.github.kperson.wal.WAL

import scala.concurrent.ExecutionContext.Implicits.global

case class Person(sex: String, firstName: String)

object Main extends App {

  val client = new DynamoClient("us-east-1")
  val wal = new WAL(client, "my_val")

  sys.exit(0)

}

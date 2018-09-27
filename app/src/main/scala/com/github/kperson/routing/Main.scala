package com.github.kperson.routing

import com.github.kperson.aws.dynamo.DynamoClient
import com.github.kperson.model.Message
import com.github.kperson.wal.WAL
import org.json4s.NoTypeHints
import org.json4s.jackson.Serialization

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

case class Person(sex: String, firstName: String)

object Main extends App {

  implicit val defaultFormats = Serialization.formats(NoTypeHints)

  val client = new DynamoClient("us-east-1")
  val wal = new WAL(client, "my_val")

  val messages = (0 to 99).map { _ => Message("abc.fdf.df", "hello", "MY_EXCHANGE") }

  //val op = wal.write(messages.toList)
  //val op = wal.loadRecords()
  val op = wal.removeRecord("0001538014797622-000000000-39c018b3db5441c8a54662c5e33068a9")
  val rs = Await.result(op, 5.seconds)

  println(rs)

  sys.exit(0)

}

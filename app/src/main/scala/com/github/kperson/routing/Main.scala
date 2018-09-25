package com.github.kperson.routing

import java.util.UUID

import com.github.kperson.aws.dynamo.DynamoClient
import org.json4s.NoTypeHints
import org.json4s.jackson.Serialization

import scala.concurrent.Await
import scala.concurrent.duration._


case class Person(id: String, firstName: String, lastName: String)

object Main extends App {

  implicit val defaultFormats = Serialization.formats(NoTypeHints)

  val person = Person(UUID.randomUUID().toString, "Kelton", "Person")
  val client = new DynamoClient("us-east-1")
  //val z = client.getItem[Person]("sample", Map("id"-> "cccc1eacd278-1cba-4e49-83e5-be64c1e4c56f"))

  val z = client.query("sample", keyConditionExpression = "id = :id", expressionAttributeValues = Map(":id" -> "cccc1eacd278-1cba-4e49-83e5-be64c1e4c56f"))
  println(Await.result(z, 10.seconds))
  System.exit(0)

}

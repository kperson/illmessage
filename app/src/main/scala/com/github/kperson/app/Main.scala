package com.github.kperson.app

import com.github.kperson.api.APIInit
import com.github.kperson.model.MessageSubscription

import scala.concurrent.duration._
import scala.concurrent.Await
import org.json4s.jackson.Serialization._


object Main extends App with APIInit {

  api.run(port = config.port)

//  val c = dynamoClient
//  import com.github.kperson.serialization.JSONFormats.formats
//
//  val sub = MessageSubscription("E1", "a.*.c", "Q1", "A1")
//
//  c.putItem()


//  val abc = c.query[Map[String, Any]](
//    table = "illmessage_dead_letter_queue",
//    keyConditionExpression = "subscriptionId = :subscriptionId",
//    filterExpression = Some("my_keys_abc[0] = :1 AND my_keys_abc[1] = :2"),
//    expressionAttributeValues = Map(
//      ":subscriptionId" -> "ABC",
//      ":1" -> "kelton",
//      ":2" -> "is"
//    )
//  )

  //println(Await.result(abc, 4.seconds))

}
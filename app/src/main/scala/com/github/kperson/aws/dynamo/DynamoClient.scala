package com.github.kperson.aws.dynamo

import com.amazonaws.auth.AWSCredentials
import com.github.kperson.aws.{Credentials, HttpRequest}

import java.nio.charset.StandardCharsets

import org.json4s.{Formats, NoTypeHints}
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization._
import org.json4s.jackson.JsonMethods._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.Manifest

import DynamoSerialization._

case class DynamoPagedResults[A](results: List[A], lastEvaluatedKey: Option[Map[String, Any]])


class DynamoClient(
  region: String,
  credentials: AWSCredentials = Credentials.defaultCredentials
) {

  private def request(payload: Array[Byte], target: String): HttpRequest = {
    new HttpRequest(
      credentials,
      "dynamodb",
      region,
      s"https://dynamodb.$region.amazonaws.com",
      method = "POST",
      headers = Map(
        "content-type" -> "application/x-amz-json-1.0",
        "X-amz-target" -> target
      ),
      payload = payload
    )
  }


  def putItem[A <: AnyRef](table: String, item: A)(implicit formats: Formats): Future[Any] = {
    val defaultFormats = Serialization.formats(NoTypeHints)

    //convert your object to JSON using the desired formats
    val jsonItem = write(item)(formats)

    //convert your object from JSON to a dynamo representation
    val dynamoItem = parse(jsonItem).rawToDynamo.get.asInstanceOf[DynamoMap]

    val bodyParams = Map(
      "Item" -> dynamoItem.asDynamo("M"),
      "TableName" -> table
    )

    //create a payload, but use the specified formats, use the exact case provide in the map
    val payload = write(bodyParams)(defaultFormats).getBytes(StandardCharsets.UTF_8)
    request(payload, "DynamoDB_20120810.PutItem").run()
  }


  def getItem[A <: AnyRef](table: String, key: Map[String, Any])(implicit formats: Formats, mf: Manifest[A]): Future[Option[A]] = {
    val defaultFormats = Serialization.formats(NoTypeHints)

    //convert your key to JSON using the desired formats
    val jsonKey = write(key)(formats)

    //convert your object from JSON to a dynamo representation
    val dynamoKey = parse(jsonKey).rawToDynamo.get.asInstanceOf[DynamoMap]

    val bodyParams = Map(
      "Key" -> dynamoKey.asDynamo("M"),
      "TableName" -> table
    )
    val payload = write(bodyParams).getBytes(StandardCharsets.UTF_8)
    request(payload, "DynamoDB_20120810.GetItem").run().map { res =>
      val m = read[Map[String, Any]](new String(res.body, StandardCharsets.UTF_8))
      if(m.contains("Item")) {
        val item = m("Item").asInstanceOf[Map[String, Any]]
        val itemDynamoJSON = parse(write(Map("M" -> item))(defaultFormats))
        val itemDynamoMap = DynamoMap(DynamoMap.unapply(itemDynamoJSON).get)
        val itemJSON = write(itemDynamoMap.flatten)(defaultFormats)
        val x = read[A](itemJSON)(formats, mf)
        Some(x)
      }
      else {
        None
      }
    }
  }

  private def mapToDyanmoMap(map: Map[String, Any]): DynamoMap = {
    val defaultFormats = Serialization.formats(NoTypeHints)
    val expressionAttributesJSON = parse(write(map)(defaultFormats))
    expressionAttributesJSON.rawToDynamo.get.asInstanceOf[DynamoMap]
  }

  def query[A <: AnyRef](
     table: String,
     projectionExpression: Option[String] = None,
     keyConditionExpression: String,
     filterExpression: Option[String] = None,
     expressionAttributeValues: Map[String, Any] = Map.empty,
     lastEvaluatedKey: Option[Map[String, Any]] = None,
     consistentRead: Boolean = false,
     indexName: Option[String] = None,
     scanIndexForward: Boolean = true,
     limit: Int = 100,
   )(implicit formats: Formats, mf: Manifest[A]): Future[Any] = {
    val defaultFormats = Serialization.formats(NoTypeHints)

    val payloadOpt: Map[String, Option[Any]] = Map(
      "TableName" -> Some(table),
      "IndexName" -> indexName,
      "Limit" -> Some(limit),
      "ConsistentRead" -> Some(consistentRead),
      "ProjectionExpression" -> projectionExpression,
      "KeyConditionExpression" -> Some(keyConditionExpression),
      "ExpressionAttributeValues" -> Some(mapToDyanmoMap(expressionAttributeValues).asDynamo("M")),
      "ScanIndexForward" -> Some(scanIndexForward),
      "FilterExpression" -> filterExpression,
      "LastEvaluatedKey" -> lastEvaluatedKey.map { mapToDyanmoMap(_).asDynamo("M") }
    )
    val bodyParams = payloadOpt.collect {
      case (k, Some(v)) => (k, v)
    }
    //create a payload, but use the specified formats, use the exact case provide in the map
    val payload = write(bodyParams)(defaultFormats).getBytes(StandardCharsets.UTF_8)
    request(payload, "DynamoDB_20120810.Query").run()
  }

}

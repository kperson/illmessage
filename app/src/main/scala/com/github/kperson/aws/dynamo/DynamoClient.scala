package com.github.kperson.aws.dynamo

import com.amazonaws.auth.AWSCredentials
import com.github.kperson.aws.{Credentials, HttpRequest}

import java.nio.charset.StandardCharsets

import org.json4s.{Formats, NoTypeHints}
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization._
import org.json4s.jackson.JsonMethods._

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.Manifest

import DynamoSerialization._


case class DynamoPagedResults[A](results: List[A], lastEvaluatedKey: Option[Map[String, Any]])


class DynamoClient(
  region: String,
  credentials: AWSCredentials = Credentials.defaultCredentials
) (implicit ec: ExecutionContext) {

  private val defaultFormats = Serialization.formats(NoTypeHints)

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

  private def mapToDynamoMap(map: Map[String, Any]): DynamoMap = {
    val defaultFormats = Serialization.formats(NoTypeHints)
    val expressionAttributesJSON = parse(write(map)(defaultFormats))
    expressionAttributesJSON.rawToDynamo.get.asInstanceOf[DynamoMap]
  }

  def putItem[A <: AnyRef](table: String, item: A)(implicit formats: Formats): Future[Any] = {
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



  def deleteItem[A <: AnyRef](table: String, key: Map[String, Any])(implicit formats: Formats, mf: Manifest[A]): Future[Option[A]] = {
    val bodyParams = Map(
      "Key" -> mapToDynamoMap(key).asDynamo("M"),
      "TableName" -> table,
      "ReturnValues" -> "ALL_OLD"
    )
    val payload = write(bodyParams).getBytes(StandardCharsets.UTF_8)
    request(payload, "DynamoDB_20120810.DeleteItem").run().map { res =>
      val m = read[Map[String, Any]](new String(res.body, StandardCharsets.UTF_8))
      if(m.contains("Attributes")) {
        val item = m("Attributes").asInstanceOf[Map[String, Any]]
        val itemDynamoJSON = parse(write(Map("M" -> item))(defaultFormats))
        val itemDynamoMap = DynamoMap(DynamoMap.unapply(itemDynamoJSON).get)
        val itemJSON = write(itemDynamoMap.flatten)(defaultFormats)
        Some(read[A](itemJSON)(formats, mf))
      }
      else {
        None
      }
    }
  }


  def getItem[A <: AnyRef](table: String, key: Map[String, Any])(implicit formats: Formats, mf: Manifest[A]): Future[Option[A]] = {
    val bodyParams = Map(
      "Key" -> mapToDynamoMap(key).asDynamo("M"),
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
        Some(read[A](itemJSON)(formats, mf))
      }
      else {
        None
      }
    }
  }

  def query[A <: AnyRef](
     table: String,
     keyConditionExpression: String,
     filterExpression: Option[String] = None,
     expressionAttributeValues: Map[String, Any] = Map.empty,
     expressionAttributeNames: Map[String, String] = Map.empty,
     lastEvaluatedKey: Option[Map[String, Any]] = None,
     consistentRead: Boolean = false,
     indexName: Option[String] = None,
     scanIndexForward: Boolean = true,
     limit: Int = 100,
   )(implicit formats: Formats, mf: Manifest[A]): Future[DynamoPagedResults[A]] = {
    val payloadOpt: Map[String, Option[Any]] = Map(
      "TableName" -> Some(table),
      "IndexName" -> indexName,
      "Limit" -> Some(limit),
      "ConsistentRead" -> Some(consistentRead),
      "KeyConditionExpression" -> Some(keyConditionExpression),
      "ExpressionAttributeValues" -> (if(expressionAttributeValues.isEmpty) None else Some(mapToDynamoMap(expressionAttributeValues).asDynamo("M"))),
      "ExpressionAttributeNames" -> (if(expressionAttributeNames.isEmpty) None else Some(expressionAttributeNames)),
      "ScanIndexForward" -> Some(scanIndexForward),
      "FilterExpression" -> filterExpression,
      "LastEvaluatedKey" -> lastEvaluatedKey
    )
    val bodyParams = payloadOpt.collect {
      case (k, Some(v)) => (k, v)
    }
    //create a payload, but use the specified formats, use the exact case provide in the map
    val payload = write(bodyParams)(defaultFormats).getBytes(StandardCharsets.UTF_8)
    request(payload, "DynamoDB_20120810.Query").run().map { res =>
      val m = read[Map[String, Any]](new String(res.body, StandardCharsets.UTF_8))
      val queryLastEvaluatedKey = m.get("LastEvaluatedKey").map { _.asInstanceOf[Map[String, Any]] }

      val items = m("Items").asInstanceOf[List[Map[String, Any]]]
      val serializeItems = items.map { item =>
        val itemDynamoJSON = parse(write(Map("M" -> item))(defaultFormats))
        val itemDynamoMap = DynamoMap(DynamoMap.unapply(itemDynamoJSON).get)
        val itemJSON = write(itemDynamoMap.flatten)(defaultFormats)
        read[A](itemJSON)(formats, mf)
      }
      DynamoPagedResults(serializeItems, queryLastEvaluatedKey)
    }
  }

}

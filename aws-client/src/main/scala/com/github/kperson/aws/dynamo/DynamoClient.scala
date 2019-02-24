package com.github.kperson.aws.dynamo

import com.amazonaws.auth.AWSCredentialsProvider
import com.github.kperson.aws.{AWSHttpResponse, Credentials, HttpRequest}

import java.nio.charset.StandardCharsets

import org.json4s.{Extraction, Formats, NoTypeHints}
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization._

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.Manifest

import DynamoSerialization._


case class DynamoPagedResults[A](results: List[A], lastEvaluatedKey: Option[Map[String, Any]])
case class BatchWriteResults[A](unprocessedInserts: List[A], unprocessedDeletes: List[Map[String, Any]])


class DynamoClient(
  region: String,
  endpointOverride: Option[String] = None,
  credentialsProvider: AWSCredentialsProvider = Credentials.defaultCredentialsProvider,
) (implicit val ec: ExecutionContext) {

  private def defaultFormats: Formats = Serialization.formats(NoTypeHints)

  private def mapToDynamoMap(map: Map[String, Any]): DynamoMap = {
    val defaultFormats: Formats = Serialization.formats(NoTypeHints)
    val expressionAttributesJSON = Extraction.decompose(map)(defaultFormats)
    expressionAttributesJSON.rawToDynamo.get.asInstanceOf[DynamoMap]
  }

  def putItem[A <: AnyRef](table: String, item: A)(implicit formats: Formats): Future[Any] = {
    //convert your object from JSON to a dynamo representation
    val dynamoItem = Extraction.decompose(item)(formats).rawToDynamo.get.asInstanceOf[DynamoMap]

    val bodyParams = Map(
      "Item" -> dynamoItem.asDynamo("M"),
      "TableName" -> table
    )

    //create a payload, but use the specified formats, use the exact case provide in the map
    val payload = write(bodyParams)(defaultFormats).getBytes(StandardCharsets.UTF_8)
    request(payload, "DynamoDB_20120810.PutItem").run()
  }

  def batchPutItems[A <: AnyRef](table: String, items: List[A], convertFromSnakeCase: Boolean = false)(implicit formats: Formats, mf: Manifest[A]): Future[BatchWriteResults[A]] = {
    require(items.size <= 25, "you may only enter 25 items per batch")
    if(items.isEmpty) {
      Future.successful(BatchWriteResults(List.empty, List.empty))
    }
    else {
      val dynamoItems = items.map { item =>
        val dynamoItem =  Extraction.decompose(item)(formats).rawToDynamo.get.asInstanceOf[DynamoMap]
        Map("PutRequest" -> Map("Item" -> dynamoItem.asDynamo("M")))
      }
      val bodyParams = Map(
        "RequestItems" -> Map(
          table -> dynamoItems
        )
      )
      val payload = write(bodyParams)(defaultFormats).getBytes(StandardCharsets.UTF_8)
      request(payload, "DynamoDB_20120810.BatchWriteItem").run().map { res =>
        val m = read[Map[String, Any]](new String(res.body, StandardCharsets.UTF_8))
        val unprocessedItems = m("UnprocessedItems").asInstanceOf[Map[String, List[Map[String, Map[String, Map[String, Any]]]]]]
        val missingItems = unprocessedItems.getOrElse(table, List.empty).map { _("PutRequest")("Item") }
        val uis = missingItems.map { item =>
          val itemDynamoJSON = Extraction.decompose(Map("M" -> item))(defaultFormats)
          val itemDynamoMap = DynamoMap(DynamoMap.unapply(itemDynamoJSON).get)
          if(convertFromSnakeCase) {
            val itemJSON = Extraction.decompose(itemDynamoMap.flatten)(defaultFormats)
            itemJSON.camelizeKeys.extract[A](formats, mf)
          }
          else {
            val itemJSON = Extraction.decompose(itemDynamoMap.flatten)(defaultFormats)
            itemJSON.extract[A](formats, mf)
          }
        }
        BatchWriteResults(uis, List.empty)
      }
    }
  }

  def batchDeleteItems(table: String, keys: List[Map[String, Any]]): Future[BatchWriteResults[Map[String, Any]]] = {
    implicit val formats: Formats = Serialization.formats(NoTypeHints)
    require(keys.size <= 25, "you may only delete 25 items per batch")
    if(keys.isEmpty) {
      Future.successful(BatchWriteResults(List.empty, List.empty))
    }
    else {
      val dynamoItems = keys.map { item =>
        val dynamoItem = Extraction.decompose(item).rawToDynamo.get.asInstanceOf[DynamoMap]
        Map("DeleteRequest" -> Map("Key" -> dynamoItem.asDynamo("M")))
      }
      val bodyParams = Map(
        "RequestItems" -> Map(
          table -> dynamoItems
        )
      )
      val payload = write(bodyParams).getBytes(StandardCharsets.UTF_8)
      request(payload, "DynamoDB_20120810.BatchWriteItem").run().map { res =>
        val m = read[Map[String, Any]](new String(res.body, StandardCharsets.UTF_8))
        val unprocessedItems = m("UnprocessedItems").asInstanceOf[Map[String, List[Map[String, Map[String, Map[String, Any]]]]]]
        val missingItems = unprocessedItems.getOrElse(table, List.empty).map { _("PutRequest")("Item") }
        val uis = missingItems.map { item =>
          val itemDynamoJSON = Extraction.decompose(Map("M" -> item))
          val itemDynamoMap = DynamoMap(DynamoMap.unapply(itemDynamoJSON).get)
          itemDynamoMap.flatten
        }
        BatchWriteResults(List.empty, uis)
      }
    }
  }

  private def readList[A <: AnyRef](response: AWSHttpResponse, convertFromSnakeCase: Boolean)(implicit formats: Formats, mf: Manifest[A]) = {
    val m = read[Map[String, Any]](new String(response.body, StandardCharsets.UTF_8))
    val queryLastEvaluatedKey = m.get("LastEvaluatedKey").map { _.asInstanceOf[Map[String, Any]] }

    val items = m("Items").asInstanceOf[List[Map[String, Any]]]
    val serializeItems = items.map { item =>
      val itemDynamoJSON = Extraction.decompose(Map("M" -> item))(defaultFormats)
      val itemDynamoMap = DynamoMap(DynamoMap.unapply(itemDynamoJSON).get)
      if(convertFromSnakeCase) {
        val itemJSON = Extraction.decompose(itemDynamoMap.flatten)(defaultFormats)
        itemJSON.camelizeKeys.extract[A](formats, mf)
      }
      else {
        val itemJSON = Extraction.decompose(itemDynamoMap.flatten)(defaultFormats)
        itemJSON.extract[A](formats, mf)
      }
    }
    DynamoPagedResults(serializeItems, queryLastEvaluatedKey)
  }

  def request(payload: Array[Byte], target: String): HttpRequest = {
    val endpoint = endpointOverride.getOrElse(s"https://dynamodb.$region.amazonaws.com")
    new HttpRequest(
      credentialsProvider.getCredentials,
      "dynamodb",
      region,
      endpoint,
      method = "POST",
      headers = Map(
        "content-type" -> "application/x-amz-json-1.0",
        "X-amz-target" -> target
      ),
      payload = payload
    )
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
        val itemDynamoJSON = Extraction.decompose(Map("M" -> item))(defaultFormats)
        val itemDynamoMap = DynamoMap(DynamoMap.unapply(itemDynamoJSON).get)
        val aOpt = Extraction.decompose(itemDynamoMap.flatten)(defaultFormats).extract[A](formats, mf)
        Some(aOpt)
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
        val itemDynamoJSON = Extraction.decompose(Map("M" -> item))(defaultFormats)
        val itemDynamoMap = DynamoMap(DynamoMap.unapply(itemDynamoJSON).get)
        val aOpt = Extraction.decompose(itemDynamoMap.flatten)(defaultFormats).extract[A](formats, mf)
        Some(aOpt)
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
     convertFromSnakeCase: Boolean = false,
     projection: Option[List[String]] = None
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
      "LastEvaluatedKey" -> lastEvaluatedKey,
      "ProjectionExpression" -> projection.map { _.mkString(", ") }
    )
    val bodyParams = payloadOpt.collect {
      case (k, Some(v)) => (k, v)
    }
    //create a payload, but use the specified formats, use the exact case provide in the map
    val payload = write(bodyParams)(defaultFormats).getBytes(StandardCharsets.UTF_8)
    request(payload, "DynamoDB_20120810.Query").run().map { res =>
      readList[A](res, convertFromSnakeCase)
    }
  }

  def scan[A <: AnyRef](
    table: String,
    filterExpression: Option[String] = None,
    expressionAttributeValues: Map[String, Any] = Map.empty,
    expressionAttributeNames: Map[String, String] = Map.empty,
    exclusiveStartKey: Option[Map[String, Any]] = None,
    consistentRead: Boolean = false,
    limit: Int = 100,
    convertFromSnakeCase: Boolean = false
   )(implicit formats: Formats, mf: Manifest[A]): Future[DynamoPagedResults[A]] = {
    val payloadOpt: Map[String, Option[Any]] = Map(
      "TableName" -> Some(table),
      "Limit" -> Some(limit),
      "ConsistentRead" -> Some(consistentRead),
      "ExpressionAttributeValues" -> (if(expressionAttributeValues.isEmpty) None else Some(mapToDynamoMap(expressionAttributeValues).asDynamo("M"))),
      "ExpressionAttributeNames" -> (if(expressionAttributeNames.isEmpty) None else Some(expressionAttributeNames)),
      "FilterExpression" -> filterExpression,
      "ExclusiveStartKey" -> exclusiveStartKey
    )
    val bodyParams = payloadOpt.collect {
      case (k, Some(v)) => (k, v)
    }
    //create a payload, but use the specified formats, use the exact case provide in the map
    val payload = write(bodyParams)(defaultFormats).getBytes(StandardCharsets.UTF_8)
    request(payload, "DynamoDB_20120810.Scan").run().map { res =>
      readList[A](res, convertFromSnakeCase)
    }
  }

}
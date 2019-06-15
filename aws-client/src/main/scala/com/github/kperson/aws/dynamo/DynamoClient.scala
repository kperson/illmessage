package com.github.kperson.aws.dynamo

import com.amazonaws.auth.AWSCredentialsProvider
import com.github.kperson.aws.{AWSHttpResponse, Credentials, HttpRequest}
import java.nio.charset.StandardCharsets

import scala.concurrent.{ExecutionContext, Future}
import DynamoSerialization._
import play.api.libs.json._


case class DynamoPagedResults[A](results: List[A], lastEvaluatedKey: Option[Map[String, Any]])
case class BatchWriteResults[A](unprocessedInserts: List[A], unprocessedDeletes: List[Map[String, Any]])


class DynamoClient(
  region: String,
  endpointOverride: Option[String] = None,
  credentialsProvider: AWSCredentialsProvider = Credentials.defaultCredentialsProvider
) (implicit val ec: ExecutionContext) {

  implicit val mapWrites: Writes[Map[String, Any]] = { o =>
    DynamoPrimitive.fromAny(o)
  }

  implicit val mapReads: Reads[Map[String, Any]] = { o =>
    val x = o.asInstanceOf[JsObject].value.map { case (k, v) => (k, v.rawToDynamo.flatten) }.toMap
    JsSuccess(x)
  }


  private def mapToDynamoMap(map: Map[String, Any]): DynamoMap = {
    val expressionAttributesJSON = Json.toJson(map)
    expressionAttributesJSON.rawToDynamo.asInstanceOf[DynamoMap]
  }

  def putItem[A](table: String, item: A)(implicit writes: Writes[A]): Future[Any] = {
    //convert your object from JSON to a dynamo representation
    val dynamoItem = Json.toJson(item).rawToDynamo.asInstanceOf[DynamoMap]
    val bodyParams = Map(
      "Item" -> dynamoItem.asDynamo("M"),
      "TableName" -> table
    )

    //create a payload, but use the specified formats, use the exact case provide in the map
    val payload = Json.toJson(bodyParams).toString().getBytes(StandardCharsets.UTF_8)
    request(payload, "DynamoDB_20120810.PutItem").run()
  }

  def batchPutItems[A](table: String, items: List[A])(implicit writes: Writes[A], reads: Reads[A]): Future[BatchWriteResults[A]] = {
    require(items.size <= 25, "you may only enter 25 items per batch")
    if(items.isEmpty) {
      Future.successful(BatchWriteResults(List.empty, List.empty))
    }
    else {
      val dynamoItems = items.map { item =>
        val dynamoItem = Json.toJson(item).rawToDynamo.asInstanceOf[DynamoMap]
        Map("PutRequest" -> Map("Item" -> dynamoItem.asDynamo("M")))
      }
      val bodyParams = Map(
        "RequestItems" -> Map(
          table -> dynamoItems
        )
      )
      val payload = Json.toJson(bodyParams).toString().getBytes(StandardCharsets.UTF_8)
      request(payload, "DynamoDB_20120810.BatchWriteItem").run().map { res =>
        val m = Json.fromJson[Map[String, Any]](Json.parse(res.body)).get
        val unprocessedItems = m("UnprocessedItems").asInstanceOf[Map[String, List[Map[String, Map[String, Map[String, Any]]]]]]
        val missingItems = unprocessedItems.getOrElse(table, List.empty).map { _("PutRequest")("Item") }
        val uis = missingItems.map { item =>
          val itemDynamoJSON = Json.toJson(Map("M" -> item))
          val itemDynamoMap = DynamoMap(DynamoMap.unapply(itemDynamoJSON).get)
          val itemJSON = Json.toJson(itemDynamoMap.flatten)
          Json.fromJson[A](itemJSON).get
        }
        BatchWriteResults(uis, List.empty)
      }
    }
  }

  def batchDeleteItems(table: String, keys: List[Map[String, Any]]): Future[BatchWriteResults[Map[String, Any]]] = {
    require(keys.size <= 25, "you may only delete 25 items per batch")
    if(keys.isEmpty) {
      Future.successful(BatchWriteResults(List.empty, List.empty))
    }
    else {
      val dynamoItems = keys.map { item =>
        val dynamoItem = Json.toJson(item).rawToDynamo.asInstanceOf[DynamoMap]
        Map("DeleteRequest" -> Map("Key" -> dynamoItem.asDynamo("M")))
      }
      val bodyParams = Map(
        "RequestItems" -> Map(
          table -> dynamoItems
        )
      )
      val payload = Json.toJson(bodyParams).toString().getBytes(StandardCharsets.UTF_8)
      request(payload, "DynamoDB_20120810.BatchWriteItem").run().map { res =>
        val m = Json.fromJson[Map[String, Any]](Json.parse(res.body)).get
        val unprocessedItems = m("UnprocessedItems").asInstanceOf[Map[String, List[Map[String, Map[String, Map[String, Any]]]]]]
        val missingItems = unprocessedItems.getOrElse(table, List.empty).map { _("PutRequest")("Item") }
        val uis = missingItems.map { item =>
          val itemDynamoJSON = Json.toJson(Map("M" -> item))
          val itemDynamoMap = DynamoMap(DynamoMap.unapply(itemDynamoJSON).get)
          itemDynamoMap.flatten
        }
        BatchWriteResults(List.empty, uis)
      }
    }
  }

  private def readList[A](response: AWSHttpResponse[Array[Byte]])(implicit reads: Reads[A]) = {
    val m = Json.fromJson[Map[String, Any]](Json.parse(response.body)).get

    val queryLastEvaluatedKey = m.get("LastEvaluatedKey").map { _.asInstanceOf[Map[String, Any]] }

    val items = m("Items").asInstanceOf[List[Map[String, Any]]]
    val serializeItems = items.flatMap { item =>
      val itemDynamoMap = DynamoMap(DynamoMap.unapply(Json.toJson(Map("M" -> item))).get)
      val itemJSON = Json.toJson(itemDynamoMap.flatten)
      Json.fromJson[A](itemJSON).asOpt
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

  def deleteItem[A](table: String, key: Map[String, Any])(implicit reads: Reads[A]): Future[Option[A]] = {
    val bodyParams = Map(
      "Key" -> mapToDynamoMap(key).asDynamo("M"),
      "TableName" -> table,
      "ReturnValues" -> "ALL_OLD"
    )
    val payload = Json.toJson(bodyParams).toString().getBytes(StandardCharsets.UTF_8)
    request(payload, "DynamoDB_20120810.DeleteItem").run().map { res =>
      val m = Json.fromJson[Map[String, Any]](Json.parse(res.body)).get
      if(m.contains("Attributes")) {
        val item = m("Attributes").asInstanceOf[Map[String, Any]]
        val itemDynamoJSON = Json.toJson(Map("M" -> item))
        val itemDynamoMap = DynamoMap(DynamoMap.unapply(itemDynamoJSON).get)
        Json.fromJson[A](Json.toJson(itemDynamoMap.flatten)).asOpt
      }
      else {
        None
      }
    }
  }

  def getItem[A](table: String, key: Map[String, Any])(implicit reads: Reads[A]): Future[Option[A]] = {
    val bodyParams = Map(
      "Key" -> mapToDynamoMap(key).asDynamo("M"),
      "TableName" -> table
    )

    val payload = Json.toJson(bodyParams).toString().getBytes(StandardCharsets.UTF_8)

    request(payload, "DynamoDB_20120810.GetItem").run().map { res =>
      val m = Json.fromJson[Map[String, Any]](Json.parse(res.body)).get
      if(m.contains("Item")) {
        val item = m("Item").asInstanceOf[Map[String, Any]]
        val itemDynamoJSON = Json.toJson(Map("M" -> item))
        val itemDynamoMap = DynamoMap(DynamoMap.unapply(itemDynamoJSON).get)
        Json.fromJson[A]( Json.toJson(itemDynamoMap.flatten)).asOpt
      }
      else {
        None
      }
    }
  }

  def query[A](
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
   )(implicit reads: Reads[A]): Future[DynamoPagedResults[A]] = {
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
    val payload = Json.toJson(bodyParams).toString().getBytes(StandardCharsets.UTF_8)
    request(payload, "DynamoDB_20120810.Query").run().map { res =>
      readList[A](res)
    }
  }

  def scan[A](
    table: String,
    filterExpression: Option[String] = None,
    expressionAttributeValues: Map[String, Any] = Map.empty,
    expressionAttributeNames: Map[String, String] = Map.empty,
    exclusiveStartKey: Option[Map[String, Any]] = None,
    consistentRead: Boolean = false,
    limit: Int = 100
   )(implicit reads: Reads[A]): Future[DynamoPagedResults[A]] = {
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
    val payload = Json.toJson(bodyParams).toString().getBytes(StandardCharsets.UTF_8)
    request(payload, "DynamoDB_20120810.Scan").run().map { res =>
      readList[A](res)
    }
  }

}
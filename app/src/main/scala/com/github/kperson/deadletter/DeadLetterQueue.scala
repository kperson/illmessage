package com.github.kperson.deadletter

import com.github.kperson.aws.dynamo.DynamoClient
import com.github.kperson.model.{Message, Subscription}
import org.json4s.{Formats, NoTypeHints}
import org.json4s.jackson.Serialization

import scala.concurrent.Future
import scala.util.Success


case class DeadLetterMessage(
  subscriptionId: String,
  subscription: Subscription,
  messageId: String,
  message: Message,
  insertedAt: Long,
  ttl: Long
)

class DeadLetterQueue(client: DynamoClient, table: String) {

  implicit val defaultFormats: Formats = Serialization.formats(NoTypeHints)

  import client.ec

  def write(message: DeadLetterMessage): Future[Any] = {
    client.putItem(table, message)
  }

  def loadAndRemove(
    base: List[DeadLetterMessage] = List.empty,
    subscription: Subscription,
    lastEvaluatedKey: Option[Map[String, Any]] = None,
    currentTime: Option[Long] = None
  ): Future[List[DeadLetterMessage]] = {
    val t = currentTime.getOrElse(System.currentTimeMillis)
    val f = client.query[DeadLetterMessage](
      table,
      "subscriptionId = :subscriptionId",
      filterExpression = Some(":insertedAt < :time"),
      expressionAttributeValues = Map(":subscriptionId" -> subscription.id, ":time" -> t),
      lastEvaluatedKey = lastEvaluatedKey,
      limit = 300,
      consistentRead = true
    )
    f.andThen { case Success(records) =>
      val keysToDelete = records.results.map { r => Map("subscriptionId" -> r.subscriptionId, "messageId" -> r.messageId) }
      deleteKeys(keysToDelete)
    }.flatMap { records =>
      val newBase = base ++ records.results
      if(records.results.isEmpty || records.lastEvaluatedKey.isEmpty) {
        Future.successful(newBase)
      }
      else {
        loadAndRemove(newBase, subscription, records.lastEvaluatedKey, Some(t))
      }
    }
  }

  private def deleteKeys(keys: List[Map[String, Any]]): Future[Any] = {
    client.batchDeleteItems(table, keys).flatMap { rs =>
      if(rs.unprocessedDeletes.nonEmpty) {
        deleteKeys(rs.unprocessedDeletes)
      }
      else {
        Future.successful(true)
      }
    }
  }

}

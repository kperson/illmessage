package com.github.kperson.deadletter

import java.util.UUID

import com.github.kperson.aws.dynamo.DynamoClient
import com.github.kperson.model.{Message, MessageSubscription}
import com.github.kperson.wal.WAL

import org.json4s.{Formats, NoTypeHints}
import org.json4s.jackson.Serialization

import scala.concurrent.Future
import scala.util.Success


case class DeadLetterMessage(
                              subscriptionId: String,
                              subscription: MessageSubscription,
                              messageId: String,
                              message: Message,
                              insertedAt: Long,
                              ttl: Long
)

class DeadLetterQueue(client: DynamoClient, table: String, wal: WAL) {

  implicit val defaultFormats: Formats = Serialization.formats(NoTypeHints)

  import client.ec

  def write(message: DeadLetterMessage): Future[Any] = {
    client.putItem(table, message)
  }

  def loadToWAL(
                 subscription: MessageSubscription,
                 base: List[(String, Message)] = List.empty,
                 lastEvaluatedKey: Option[Map[String, Any]] = None,
                 currentTime: Option[Long] = None,
                 batchId: Option[String] = None
  ): Future[List[(String, Message)]] = {
    val bId = batchId.getOrElse(UUID.randomUUID().toString.replace("-", ""))
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
    f.flatMap { records =>
      val messageSubscriptionsGroups = records.results.map { r =>
        (r.message, r.subscription, r.messageId)
      }.grouped(25)
      val transfers = messageSubscriptionsGroups.toList.map { group =>
        val walInsert = group.map { case (m, s, _) => (m, Some(s)) }
        //move the records to wal
        wal.writeWithSubscription(walInsert)
        .andThen { case Success(_) =>
          //delete the records from the dead letter queue
          deleteKeys(group.map { case (_, s, mId) => Map("subscriptionId" -> s.id, "messageId" -> mId) })
        }
      }
      Future.sequence(transfers).map { groupedTransfers =>
        groupedTransfers.flatten.zip(records.results.map(_.message))
      }.map {  (_, records.lastEvaluatedKey) }
    }.flatMap { case (records, lek) =>
      val newBase = base ++ records
      lek match {
        case key @ Some(_) => loadToWAL(subscription, newBase, key, Some(t), Some(bId))
        case _ => Future.successful(newBase)
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

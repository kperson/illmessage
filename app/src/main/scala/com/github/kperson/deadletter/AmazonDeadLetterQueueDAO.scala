package com.github.kperson.deadletter

import com.github.kperson.app.TaskVPCConfig
import com.github.kperson.aws.dynamo.DynamoClient
import com.github.kperson.aws.ecs.ECSClient
import com.github.kperson.model.{Message, MessageSubscription}
import com.github.kperson.serialization.JSONFormats
import com.github.kperson.wal.WriteAheadDAO
import org.json4s.Formats

import scala.concurrent.Future



class AmazonDeadLetterQueueDAO(
  client: DynamoClient,
  table: String,
  walDAO: WriteAheadDAO,
  ecsClient: ECSClient,
  backgroundTaskDefinitionArn: String,
  taskVPCConfig: TaskVPCConfig

) extends DeadLetterQueueDAO {

  implicit val defaultFormats: Formats = JSONFormats.formats

  import client.ec

  def write(message: DeadLetterMessage): Future[Any] = {
    client.putItem(table, message)
  }


  def loadToWAL(subscription: MessageSubscription): Future[List[(String, Message)]] = {
    loadToWALHelper(subscription)
  }

  private def loadToWALHelper(
    subscription: MessageSubscription,
    base: List[(String, Message)] = List.empty,
    lastEvaluatedKey: Option[Map[String, Any]] = None,
    currentTime: Option[Long] = None,
  ): Future[List[(String, Message)]] = {
    val t = currentTime.getOrElse(System.currentTimeMillis)
    val f = client.query[DeadLetterMessage](
      table,
      "subscriptionId = :subscriptionId",
      filterExpression = Some("insertedAt < :time"),
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
        walDAO.writeWithSubscription(walInsert)
        .map { rs =>
          //delete the records from the dead letter queue
          deleteKeys(group.map { case (_, s, mId) => Map("subscriptionId" -> s.id, "messageId" -> mId) })
          rs
        }
      }
      Future.sequence(transfers).map { groupedTransfers =>
        groupedTransfers.flatten.zip(records.results.map(_.message))
      }.map {  (_, records.lastEvaluatedKey) }
    }.flatMap { case (records, lek) =>
      val newBase = base ++ records
      lek match {
        case key @ Some(_) => loadToWALHelper(subscription, newBase, key, Some(t))
        case _ => Future.successful(newBase)
      }
    }
  }

  def triggerRedeliver(subscription: MessageSubscription): Future[Any] = {
    val req = Map(
      "networkConfiguration" -> Map(
        "awsvpcConfiguration" -> Map(
          "assignPublicIp" -> "DISABLED",
          "securityGroups" -> List(taskVPCConfig.securityGroup),
          "subnets" -> List(taskVPCConfig.subnet)
        )
      ),
      "overrides" -> Map(
        "containerOverrides" -> List(
          Map(
            "command" -> List("background", "redeliver", subscription.exchange, subscription.bindingKey, subscription.queue, subscription.accountId),
            "name" -> "illmessage-background"
          )
        )
      ),
      "launchType" -> "FARGATE",
      "taskDefinition" -> backgroundTaskDefinitionArn,
      "count" -> 1
    )
    ecsClient.request(req, "AmazonEC2ContainerServiceV20141113.RunTask").run()
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

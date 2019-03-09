package com.github.kperson.subscription

import com.github.kperson.model.MessageSubscription
import com.github.kperson.aws.dynamo.DynamoClient
import com.github.kperson.serialization.JSONFormats
import org.json4s.Formats

import scala.concurrent.{ExecutionContext, Future}


class AmazonSubscriptionDAO(client: DynamoClient, table: String)(implicit ec: ExecutionContext) extends SubscriptionDAO {

  implicit val defaultFormats: Formats = JSONFormats.formats

  def fetchSubscriptions(exchange: String, routingKey: String): Future[List[MessageSubscription]] = {
    fetchSubscriptionsHelper(exchange, routingKey)
  }

  private def fetchSubscriptionsHelper(
    exchange: String,
    routingKey: String,
    lastEvaluatedKey: Option[Map[String, Any]] = None,
    base: List[MessageSubscription] = List.empty
   ): Future[List[MessageSubscription]] = {
    val routingKeyComponents: List[String] = routingKey.split("\\.").toList
    val indexAndComponents = routingKeyComponents.indices.zip(routingKeyComponents)
    val matchingFilter = indexAndComponents.map { case (index, _) =>
      s"(bindingKeyComponents[$index] = :$index OR bindingKeyComponents[$index] = :star)"
    }.mkString(" AND ")
    val filter = s"(#status = :active OR #status = :transitioning OR #status = :locked) AND bindingKeyComponentsSize = :componentsSize AND ($matchingFilter)"
    val query = client.query[MessageSubscription](
      table,
      "exchange = :exchange",
      Some(filter),
      expressionAttributeNames = Map(
        "#status" -> "status"
      ),
      expressionAttributeValues = Map(
        ":exchange" -> exchange,
        ":star" -> "*",
        ":active" -> "active",
        ":transitioning" -> "transitioning",
        ":locked" -> "locked",
        ":componentsSize" -> routingKeyComponents.size
      ) ++ indexAndComponents.map { case (index, key) =>
        s":$index" -> key
      }.toMap,
      lastEvaluatedKey = lastEvaluatedKey
    )
    query.flatMap { rs =>
      rs.lastEvaluatedKey match {
        case Some(k) => fetchSubscriptionsHelper(exchange, routingKey, Some(k), base ++ rs.results)
        case _ => Future.successful(base ++ rs.results)
      }
    }
  }


  def delete(exchange: String, subscriptionId: String): Future[Option[MessageSubscription]] = {
    client.deleteItem[MessageSubscription](table, Map("exchange" -> exchange, "subscriptionId" -> subscriptionId))
  }

  def save(subscription: MessageSubscription): Future[MessageSubscription] = {
    client.putItem(table, subscription).map { _ => subscription }
  }

}
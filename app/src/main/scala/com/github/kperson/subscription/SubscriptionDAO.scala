package com.github.kperson.subscription

import com.github.kperson.model.MessageSubscription

import scala.concurrent.Future


trait SubscriptionDAO {

  def save(subscription: MessageSubscription): Future[MessageSubscription]

  def fetchSubscriptions(exchange: String, routingKey: String): Future[List[MessageSubscription]]

  def delete(exchange: String, subscriptionId: String): Future[Option[MessageSubscription]]


}

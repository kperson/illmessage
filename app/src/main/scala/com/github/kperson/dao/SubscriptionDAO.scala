package com.github.kperson.dao

import com.github.kperson.model.MessageSubscription

import scala.concurrent.Future

trait SubscriptionDAO {

  def save(subscription: MessageSubscription): Future[MessageSubscription]

  def fetch(subscriptionId: String): Future[Option[MessageSubscription]]

  def delete(subscriptionId: String): Future[Option[MessageSubscription]]

}

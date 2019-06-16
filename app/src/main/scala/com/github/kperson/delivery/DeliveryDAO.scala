package com.github.kperson.delivery

import com.github.kperson.model.MessageSubscription
import com.github.kperson.wal.WALRecord

import scala.concurrent.Future

case class AckRequest(subscriptionId: String, groupId: String, sequenceId: Long)

trait DeliveryDAO {

  def queueMessages(subscriptions: List[MessageSubscription], record: WALRecord): Future[Any]
  def remove(delivery: Delivery): Future[Any]
  def ack(subscriptionId: String, groupId: String, sequenceId: Long): Future[Any]
  def bulkAck(requests: List[AckRequest]): Future[Any]
  def markDeadLetter(delivery: Delivery, throwable: Throwable): Future[Any]

}
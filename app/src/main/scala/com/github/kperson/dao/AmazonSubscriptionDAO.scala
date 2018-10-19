package com.github.kperson.dao

import com.github.kperson.aws.s3.S3Client
import com.github.kperson.model.MessageSubscription
import com.github.kperson.aws.AWSError

import java.nio.charset.StandardCharsets

import org.json4s.NoTypeHints
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization._

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.concurrent.stm.{atomic, Ref}


class AmazonSubscriptionDAO(bucket: String, s3Client: S3Client)(implicit ec: ExecutionContext) extends SubscriptionDAO {

  implicit val defaultFormats = Serialization.formats(NoTypeHints)

  private val subscriptions = Ref(Map[String, MessageSubscription]())
  private val fileKey = "/subscriptions.json"

  Await.result(load(), 7.seconds)

  def fetch(subscriptionId: String): Future[Option[MessageSubscription]] = {
    Future.successful(subscriptions.single.get.get(subscriptionId))
  }

  def fetchAllSubscriptions(): Future[List[MessageSubscription]] = {
    Future.successful(subscriptions.single.get.values.toList)
  }

  def delete(subscriptionId: String): Future[Option[MessageSubscription]] = {
    val (newSubscriptions, removed) = atomic { implicit tx =>
      subscriptions.transformAndExtract { old =>
        (old - subscriptionId, (old - subscriptionId, old.get(subscriptionId))) }
    }
    s3Client.put(bucket, fileKey, write(newSubscriptions).getBytes(StandardCharsets.UTF_8), contentType = "application/json")
      .map { _ => removed }
  }

  def save(subscription: MessageSubscription): Future[MessageSubscription] = {
    val newSubscriptions = atomic { implicit tx =>
      subscriptions.transformAndGet { _ + (subscription.id -> subscription) }
    }
    val f = s3Client.put(bucket, fileKey, write(newSubscriptions).getBytes(StandardCharsets.UTF_8), contentType = "application/json")
    f.map { _ => subscription }
  }

  private def load(): Future[Any] = {
    s3Client.get(bucket, fileKey).map { f =>
      read[Map[String, MessageSubscription]](new String(f.body, StandardCharsets.UTF_8))
    }.recover {
      case ex: AWSError if ex.response.status == 404 =>
        Map[String, MessageSubscription]()
    }.map { s =>
      atomic { implicit tx =>
        subscriptions.set(s)
      }
    }
  }

}
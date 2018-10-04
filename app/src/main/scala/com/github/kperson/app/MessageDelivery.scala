package com.github.kperson.app

import akka.NotUsed
import akka.stream.scaladsl.Flow

import com.github.kperson.aws.sqs.SQSClient
import com.github.kperson.deadletter.{DeadLetterMessage, DeadLetterQueue}
import com.github.kperson.routing.MessagePayload

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._


object MessageDelivery {

  def apply(sqsClient: SQSClient, deadLetterQueue: DeadLetterQueue)(implicit ec: ExecutionContext): Flow[List[MessagePayload], List[MessagePayload], NotUsed] = {
    Flow[List[MessagePayload]].mapAsyncUnordered(5) { ms =>
      val payloads = ms.map { payload => (
        payload.message.body,
        payload.message.delayInSeconds.map { _.seconds },
        Some(payload.subscription.id),
        Some(payload.messageId)
      )}
      sqsClient.sendMessages(
        ms.head.subscription.queue,
        payloads,
        messageAccountId = Some(ms.head.subscription.accountId)
      ).recoverWith { case ex: Throwable =>
        Future.sequence(ms.map { m =>
          deadLetterQueue.write(
            DeadLetterMessage(
              m.subscription,
              m.messageId,
              m.message,
              System.currentTimeMillis(),
              (System.currentTimeMillis() / 1000L) + 14.days.toSeconds,
              ex.getMessage
            )
          )
        })
      }.map { _ => ms }
    }
  }

}

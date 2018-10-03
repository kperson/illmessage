package com.github.kperson.app

import akka.NotUsed
import akka.stream.scaladsl.Flow

import com.github.kperson.aws.sqs.SQSClient
import com.github.kperson.deadletter.{DeadLetterMessage, DeadLetterQueue}
import com.github.kperson.routing.MessagePayload

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._


object MessageDelivery {

  def apply(sqsClient: SQSClient, deadLetterQueue: DeadLetterQueue)(implicit ex: ExecutionContext): Flow[MessagePayload, MessagePayload, NotUsed] = {
    Flow[MessagePayload].mapAsyncUnordered(5) { ms =>
      sqsClient.sendMessage(
        ms.subscription.queue,
        ms.message.body,
        delay = ms.message.delayInSeconds.map { _.seconds },
        messageAccountId = Some(ms.subscription.accountId)
      )
      .recoverWith { case ex: Throwable =>
        deadLetterQueue.write(
          DeadLetterMessage(
            ms.subscription,
            ms.messageId,
            ms.message,
            System.currentTimeMillis(),
            (System.currentTimeMillis() / 1000L) + 14.days.toSeconds,
            ex.getMessage
          )
        )
      }.map { _ => ms }
    }
  }

}

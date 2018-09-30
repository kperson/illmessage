package com.github.kperson.api

import akka.http.scaladsl.server
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._

import java.util.{Timer, TimerTask}
import java.util.concurrent.TimeoutException

import com.github.kperson.model.Message
import com.github.kperson.wal.{Transfer, WALTransfer}

import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.util.Failure


class MessageWithTimeout(
  val ttl: Long,
  val messages: List[Message],
  promise: Promise[Any]
) extends Transfer {

  def onTransfer() {
    promise.success(true)
  }

  def messageId: Option[String] = None

  def preComputedSubscription = None

}

trait MessageAPI extends MarshallingSupport {

  val timeout = 4.seconds

  def walTransfer: WALTransfer

  def messageRoute: server.Route = {
    path("messages") {
      post {
        decodeRequest {
          entity(as[List[Message]]) { messages =>
            val timer = new Timer()
            val p = Promise[Any]()
            timer.schedule(new TimerTask {
              def run() {
                if(!p.isCompleted) {
                  p.failure(new TimeoutException("wal write timed out"))
                }
              }
            }, timeout.toMillis + 1.second.toMillis) //one second lax time

            val transfer = new MessageWithTimeout(System.currentTimeMillis + timeout.toMillis, messages, p)
            walTransfer.add(transfer)

            onComplete(p.future) {
              case Failure(_: TimeoutException) =>
                complete((StatusCodes.RequestTimeout, Map("error" -> "server is too busy, your request has been reject, please try again later")))
              case _ =>
                timer.cancel()
                complete((StatusCodes.OK, messages))
            }
          }
        }
      }
    }
  }

}
package com.github.kperson.util

import java.util.{Timer, TimerTask}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

object Backoff {

  def runBackoffTask[A](
    maxAttempts: Int,
    multiple: Int,
    todos: List[A],
  )(f: List[A] => Future[List[A]])
 (implicit  ec: ExecutionContext): Future[Boolean] = {
    val p = Promise[Boolean]()
    runBackoffTaskHelper(maxAttempts, multiple, todos, f, p, 0.seconds)
    p.future
  }

  private def runBackoffTaskHelper[A](
    maxAttempts: Int,
    multiple: Int,
    todos: List[A],
    f: List[A] => Future[List[A]],
    promise: Promise[Boolean],
    delay: FiniteDuration
  )(implicit  ec: ExecutionContext) {
    val rs = f(todos)
    rs.foreach {
      case Nil => promise.success(true)
      case _ if maxAttempts == 1 => promise.failure(new RuntimeException("back off job failed"))
      case remaining =>
        val timer = new Timer()
        timer.schedule(new TimerTask {
          def run() {
            val nextDelay = if(delay.toMillis == 0L) 250.milliseconds else (delay.toMillis * multiple).microsecond
            runBackoffTaskHelper(maxAttempts - 1, multiple, remaining, f, promise, nextDelay)
          }
        }, delay.toMillis)
    }
    rs.failed.foreach { promise.failure(_) }
  }

}

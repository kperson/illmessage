package com.github.kperson.message

import com.github.kperson.delivery.Delivery

import scala.concurrent.Future

trait QueueClient {

  def sendMessage(delivery: Delivery): Future[Any]

}

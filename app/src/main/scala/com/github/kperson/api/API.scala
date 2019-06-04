package com.github.kperson.api

import com.github.kperson.delivery.{DeliveryAPI, DeliveryDAO}
import com.github.kperson.subscription.{SubscriptionAPI, SubscriptionDAO}
import com.github.kperson.wal.{WriteAheadAPI, WriteAheadDAO}
import com.github.kperson.lambda._

import scala.concurrent.ExecutionContext


class API(
 val writeAheadDAO: WriteAheadDAO,
 val subscriptionDAO: SubscriptionDAO,
 val deliveryDAO: DeliveryDAO,
)(implicit val ec: ExecutionContext)
extends WriteAheadAPI
 with SubscriptionAPI
 with DeliveryAPI
 {

  val route:RequestHandler = writeAheadRoute.orElse(subscriptionRoute).orElse(deliveryRoute)

}

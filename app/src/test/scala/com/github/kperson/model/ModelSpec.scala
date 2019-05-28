package com.github.kperson.model

import org.scalatest.{FlatSpec, Matchers}


class ModelSpec extends FlatSpec with Matchers {

  "Subscription" should "generate an id" in {
    val subscription = MessageSubscription("e1", "b1", "q1", "a1", "active")
    subscription.id should be ("18eb47cd05c1d820350addbd8415e3cc")
  }

}

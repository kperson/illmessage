package com.github.kperson.test.spec


import org.scalamock.scalatest.MockFactory
import org.scalatest.{FlatSpec, Matchers}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}

trait IllMessageSpec
  extends FlatSpec
  with Matchers
  with MockFactory
  with ScalaFutures {


  def secondsTimeOut(seconds: Int) = timeout(Span(seconds, Seconds))

}


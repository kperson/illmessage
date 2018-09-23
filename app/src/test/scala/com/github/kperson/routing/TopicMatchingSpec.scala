package com.github.kperson.routing

import org.scalatest.{FlatSpec, Matchers}


class TopicMatchingSpec extends FlatSpec with Matchers {

  "TopicMatching" should "match word for word" in {
    TopicMatching.matchesTopicBinding("one.two.three", "one.two.three") should be (true)
    TopicMatching.matchesTopicBinding("one.two.four", "one.two.three") should be (false)
  }

  it should "match * (single) wildcards" in {
    TopicMatching.matchesTopicBinding("one.*.three.four.*.six", "one.two.three.four.five.six") should be (true)
  }

  it should "match # (repeat) wildcards" in {
    TopicMatching.matchesTopicBinding("A.B.C.#", "A.B.C.D.E.F") should be (true)
    TopicMatching.matchesTopicBinding("A.Q.C.#", "A.B.C.D.E.F") should be (false)
  }

}

package com.github.kperson.routing

import scala.annotation.tailrec

object TopicMatching {

  def matchesTopicBinding(bindingKey: String, routingKey: String): Boolean = {
    val bindingKeyComponents = bindingKey.split("\\.")
    val routingKeyComponents = routingKey.split("\\.")
    matchHelper(bindingKeyComponents, routingKeyComponents)
  }

  @tailrec private def matchHelper(
    bindingKeyComponents: Array[String],
    routingKeyComponents: Array[String],
    bindKeyIndex: Int = 0,
    routingKeyIndex: Int = 0
  ): Boolean = {
      if(routingKeyIndex < routingKeyComponents.length && bindKeyIndex < bindingKeyComponents.length) {
        if (bindingKeyComponents(bindKeyIndex) == "*") {
          matchHelper(bindingKeyComponents, routingKeyComponents, bindKeyIndex + 1, routingKeyIndex + 1)
        }
        else if (bindingKeyComponents(bindKeyIndex) == routingKeyComponents(routingKeyIndex)) {
          matchHelper(bindingKeyComponents, routingKeyComponents, bindKeyIndex + 1, routingKeyIndex + 1)
        }
        else if(bindingKeyComponents(bindKeyIndex) == "#") {
          matchHelper(bindingKeyComponents, routingKeyComponents, bindKeyIndex, routingKeyIndex + 1)
        }
        else {
          false
        }
      }
      else {
        true
      }
  }

}

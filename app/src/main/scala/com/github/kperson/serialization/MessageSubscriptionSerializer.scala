package com.github.kperson.serialization

import com.github.kperson.model.MessageSubscription
import play.api.libs.json.{JsString, Json, Reads, Writes}
import play.api.libs.json._


trait MessageSubscriptionSerializer {

  implicit val messageSubscriptionWrites: Writes[MessageSubscription] = { o =>
    val base = Json.writes[MessageSubscription].writes(o)
    base +
      ("subscriptionId", JsString(o.id)) +
      ("bindingKeyComponentsSize", JsNumber(o.bindingKeyComponents.length)) +
      ("bindingKeyComponents", JsArray(o.bindingKeyComponents.map(JsString.apply)))
  }

  implicit val messageSubscriptionReads: Reads[MessageSubscription] = { o =>
    val mapped = o.asInstanceOf[JsObject].value
    Json.reads[MessageSubscription](JsObject(mapped - "subscriptionId" - "bindingKeyComponentsSize" - "bindingKeyComponents"))
  }

}

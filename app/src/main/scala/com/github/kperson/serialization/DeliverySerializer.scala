package com.github.kperson.serialization

import com.github.kperson.delivery.{Delivery, DeliveryError}

import play.api.libs.json._


trait DeliverySerializer {

  implicit val deliverErrorWrites: Writes[DeliveryError] = Json.writes[DeliveryError]

  implicit val deliveryErrorReads: Reads[DeliveryError] = Json.reads[DeliveryError]

  implicit val deliveryWrites: Writes[Delivery] = { o =>
    val base = Json.writes[Delivery].writes(o)
    base + ("subscriptionStatus", JsString(o.subscription.status)) + ("subscriptionId", JsString(o.subscription.id))
  }


  implicit val deliveryReads: Reads[Delivery] = { o =>
    val mapped = o.asInstanceOf[JsObject].value - "subscriptionStatus" - "subscriptionId"
    Json.reads[Delivery].reads(JsObject(mapped))
  }

}

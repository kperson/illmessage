package com.github.kperson.serialization

import com.github.kperson.delivery.Delivery

import play.api.libs.json._


trait DeliverySerializer {

  implicit val deliveryWrites: Writes[Delivery] = { o =>
    val base = Json.writes[Delivery].writes(o)
    base + ("subscriptionStatus", JsString(o.subscription.status)) + ("subscriptionId", JsString(o.subscription.id))
  }


  implicit val deliveryReads: Reads[Delivery] = { o =>
    val mapped = o.asInstanceOf[JsObject].value.filter { case (k, _) => !List("subscriptionStatus", "subscriptionId").contains(k)  }
    Json.reads[Delivery].reads(JsObject(mapped))
  }

}

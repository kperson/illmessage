package com.github.kperson.serialization

import com.github.kperson.delivery.DeliverySerializer
import org.json4s.{Formats, NoTypeHints}
import org.json4s.jackson.Serialization

object JSONFormats {

  implicit val formats: Formats = Serialization.formats(NoTypeHints) ++ (
    new WALRecordSerializer() ::
    new MethodSerializer() ::
    new MessageSubscriptionSerializer() ::
    new DeliverySerializer() ::
    Nil
  )

}
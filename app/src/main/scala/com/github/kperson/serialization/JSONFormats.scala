package com.github.kperson.serialization

import org.json4s.{Formats, NoTypeHints}
import org.json4s.jackson.Serialization

object JSONFormats {

  implicit val formats: Formats = Serialization.formats(NoTypeHints) ++ (
    new DeadLetterMessageSerializer() ::
    new WALRecordSerializer() ::
    Nil
  )

}
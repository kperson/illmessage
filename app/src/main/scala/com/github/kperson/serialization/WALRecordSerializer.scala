package com.github.kperson.serialization

import com.github.kperson.wal.WALRecord

import play.api.libs.json._


trait WALRecordSerializer {


  implicit val walRecordSerializerWrites: Writes[WALRecord] = { o =>
    val base = Json.writes[WALRecord].writes(o)
    base + ("partitionKey", JsString(o.message.partitionKey))
  }

  implicit val walRecordSerializerReads: Reads[WALRecord] = { o =>
    val mapped = o.asInstanceOf[JsObject].value.filter { case (k, _) => !List("partitionKey").contains(k)  }
    Json.reads[WALRecord].reads(JsObject(mapped))
  }
}
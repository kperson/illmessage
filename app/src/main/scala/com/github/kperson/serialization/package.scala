package com.github.kperson

import java.io.InputStream

import com.github.kperson.aws.dynamo.{DynamoPrimitive, DynamoSerialization}
import com.github.kperson.cf.{CFRegistration, CFRequest, CFResponse}
import com.github.kperson.delivery.AckRequest
import com.github.kperson.message.FinalDelivery
import com.github.kperson.model.Message
import com.github.kperson.wal.MessagePayload
import play.api.libs.json._

import DynamoSerialization._



package object serialization
  extends DeliverySerializer
  with MessageSubscriptionSerializer
  with MethodSerializer
  with WALRecordSerializer {

  implicit val mapWrites: Writes[Map[String, Any]] = { o =>
    DynamoPrimitive.fromAny(o)
  }

  implicit val mapReads: Reads[Map[String, Any]] = { o =>
    val x = o.asInstanceOf[JsObject].value.map { case (k, v) => (k, v.rawToDynamo.flatten) }.toMap
    JsSuccess(x)
  }

  implicit val messageWrites: Writes[Message] = Json.writes[Message]
  implicit val messageReads: Reads[Message] = Json.reads[Message]

  implicit val messagePayloadWrites: Writes[MessagePayload] = Json.writes[MessagePayload]
  implicit val messagePayloadReads: Reads[MessagePayload] = Json.reads[MessagePayload]


  implicit val cfRequestWrites: Writes[CFRequest] = Json.writes[CFRequest]
  implicit val cfRequestReads: Reads[CFRequest] = Json.reads[CFRequest]

  implicit val cfRegistrationWrites: Writes[CFRegistration] = Json.writes[CFRegistration]
  implicit val cfRegistrationReads: Reads[CFRegistration] = Json.reads[CFRegistration]

  implicit val cfResponseWrites: Writes[CFResponse] = Json.writes[CFResponse]
  implicit val cfResponseReads: Reads[CFResponse] = Json.reads[CFResponse]

  implicit val ackRequestWrites: Writes[AckRequest] = Json.writes[AckRequest]
  implicit val ackRequestReads: Reads[AckRequest] = Json.reads[AckRequest]

  implicit val finalDeliveryWrites: Writes[FinalDelivery] = Json.writes[FinalDelivery]
  implicit val finalDeliveryReads: Reads[FinalDelivery] = Json.reads[FinalDelivery]


  def writeJSON[A](value: A)(implicit tjs: Writes[A]): String = {
    Json.toJson(value).toString()
  }

  def readJSON[A](value: InputStream)(implicit tjs: Reads[A]): A = {
    Json.fromJson[A](Json.parse(value)).get
  }

  def readJSON[A](value: String)(implicit tjs: Reads[A]): A = {
    Json.fromJson[A](Json.parse(value)).get
  }




}


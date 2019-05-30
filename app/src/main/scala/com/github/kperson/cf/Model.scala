package com.github.kperson.cf

import com.github.kperson.model.MessageSubscription

case class CFRequest(
  RequestType: String,
  ResponseURL: String,
  StackId: String,
  RequestId: String,
  PhysicalResourceId: Option[String],
  LogicalResourceId: String,
  ResourceProperties: Option[Map[String, Any]]
) {

  val properties: Map[String, Any] = ResourceProperties.getOrElse(Map.empty)

  def hasRequiredKeys: Boolean =
    List("Exchange", "BindingKey", "Queue")
    .forall(properties.contains)


  def subscription(defaultAccountId: String): Option[MessageSubscription] = {
    if(hasRequiredKeys) {
      Some(
        MessageSubscription(
          properties("Exchange").asInstanceOf[String],
          properties("BindingKey").asInstanceOf[String],
          properties("Queue").asInstanceOf[String],
          properties.get("AccountId").map { _.asInstanceOf[String] }.getOrElse(defaultAccountId),
          "active"
        ))
    }
    else {
      None
    }
  }

}

case class CFResponse(
  Status: String,
  Reason: Option[String],
  PhysicalResourceId: String,
  StackId: String,
  RequestId: String,
  LogicalResourceId: String,
  Data: Map[String, Any] = Map.empty
) {
  assert(List("FAILED", "SUCCESS").contains(Status), "status must be FAILED or SUCCESS")
  if(Status == "FAILED") {
    assert(Reason.isDefined, "a reason must be provider if status is FAILED")
  }
}
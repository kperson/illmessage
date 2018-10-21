variable "max_capacity" {
  default = 5000
}

resource "aws_dynamodb_table" "dead_letter_queue" {
  name           = "${var.namespace}_dead_letter_queue"
  read_capacity  = 3
  write_capacity = 3
  hash_key       = "subscriptionId"
  range_key      = "messageId"

  attribute {
    name = "subscriptionId"
    type = "S"
  }

  attribute {
    name = "messageId"
    type = "S"
  }

  ttl {
    attribute_name = "ttl"
    enabled        = true
  }

  server_side_encryption {
    enabled = true
  }

  stream_enabled = false

  ignore_changes = ["read_capacity", "write_capacity"]
}

resource "aws_dynamodb_table" "write_ahead_log" {
  name           = "${var.namespace}_write_ahead_log"
  read_capacity  = 3
  write_capacity = 3
  hash_key       = "partitionKey"
  range_key      = "messageId"

  attribute {
    name = "partitionKey"
    type = "S"
  }

  attribute {
    name = "messageId"
    type = "S"
  }

  server_side_encryption {
    enabled = true
  }

  stream_enabled = true

  stream_view_type = "NEW_AND_OLD_IMAGES"

  ignore_changes = ["read_capacity", "write_capacity"]
}

resource "aws_dynamodb_table" "subscriptions" {
  name           = "${var.namespace}_subscriptions"
  read_capacity  = 3
  write_capacity = 3
  hash_key       = "exchange"
  range_key      = "subscriptionId"

  attribute {
    name = "exchange"
    type = "S"
  }

  attribute {
    name = "subscriptionId"
    type = "S"
  }

  server_side_encryption {
    enabled = true
  }

  stream_enabled = false

  ignore_changes = ["read_capacity", "write_capacity"]
}

module "scale_write_ahead_log" {
  source     = "../modules/dynamo-scale"
  namespace  = "${var.namespace}"
  table_name = "${aws_dynamodb_table.write_ahead_log.id}"
  table_arn  = "${aws_dynamodb_table.write_ahead_log.arn}"
}

module "scale_dead_letter_queue" {
  source     = "../modules/dynamo-scale"
  namespace  = "${var.namespace}"
  table_name = "${aws_dynamodb_table.dead_letter_queue.id}"
  table_arn  = "${aws_dynamodb_table.dead_letter_queue.arn}"
}

module "scale_dead_letter_subscriptions" {
  source     = "../modules/dynamo-scale"
  namespace  = "${var.namespace}"
  table_name = "${aws_dynamodb_table.subscriptions.id}"
  table_arn  = "${aws_dynamodb_table.subscriptions.arn}"
}

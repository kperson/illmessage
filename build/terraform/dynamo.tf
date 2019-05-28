variable "max_capacity" {
  default = 5000
}

resource "aws_dynamodb_table" "mailbox" {
  name           = "${var.namespace}_mailbox"
  read_capacity  = 3
  write_capacity = 3
  hash_key       = "subscriptionId"
  range_key      = "sequenceId"

  attribute {
    name = "subscriptionId"
    type = "S"
  }

  attribute {
    name = "sequenceId"
    type = "N"
  }

  server_side_encryption {
    enabled = true
  }

  stream_enabled = true

  lifecycle {
    ignore_changes = ["read_capacity", "write_capacity"]
  }
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

  lifecycle {
    ignore_changes = ["read_capacity", "write_capacity"]
  }
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

  lifecycle {
    ignore_changes = ["read_capacity", "write_capacity"]
  }
}

resource "aws_dynamodb_table" "sub_message_sequence" {
  name           = "${var.namespace}_sub_message_sequence"
  read_capacity  = 3
  write_capacity = 3
  hash_key       = "subscriptionId"
  range_key      = "groupId"

  attribute {
    name = "subscriptionId"
    type = "S"
  }

  attribute {
    name = "groupId"
    type = "S"
  }

  server_side_encryption {
    enabled = true
  }

  stream_enabled = true

  stream_view_type = "NEW_AND_OLD_IMAGES"

  lifecycle {
    ignore_changes = ["read_capacity", "write_capacity"]
  }
}

module "scale_write_ahead_log" {
  source     = "../modules/dynamo-scale"
  namespace  = "${var.namespace}"
  table_name = "${aws_dynamodb_table.write_ahead_log.id}"
  table_arn  = "${aws_dynamodb_table.write_ahead_log.arn}"
}

module "scale_mailbox" {
  source     = "../modules/dynamo-scale"
  namespace  = "${var.namespace}"
  table_name = "${aws_dynamodb_table.mailbox.id}"
  table_arn  = "${aws_dynamodb_table.mailbox.arn}"
}

module "scale_subscriptions" {
  source     = "../modules/dynamo-scale"
  namespace  = "${var.namespace}"
  table_name = "${aws_dynamodb_table.subscriptions.id}"
  table_arn  = "${aws_dynamodb_table.subscriptions.arn}"
}

module "scale_sub_message_sequence" {
  source     = "../modules/dynamo-scale"
  namespace  = "${var.namespace}"
  table_name = "${aws_dynamodb_table.sub_message_sequence.id}"
  table_arn  = "${aws_dynamodb_table.sub_message_sequence.arn}"
}

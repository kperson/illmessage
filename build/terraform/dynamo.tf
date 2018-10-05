variable "max_capacity" {
  default = 5000
}

resource "aws_dynamodb_table" "dead_letter_queue" {
  name           = "${var.namespace}_dead_letter_queue"
  read_capacity  = 5
  write_capacity = 5
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
}

resource "aws_dynamodb_table" "write_ahead_log" {
  name           = "${var.namespace}_write_ahead_log"
  read_capacity  = 5
  write_capacity = 5
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

  stream_enabled = false
}

module "scale_write_ahead_log" {
  source     = "../modules/dynamo-scale"
  table_name = "${aws_dynamodb_table.write_ahead_log.id}"
  table_arn  = "${aws_dynamodb_table.write_ahead_log.arn}"
}

module "scale_dead_letter_queue" {
  source     = "../modules/dynamo-scale"
  table_name = "${aws_dynamodb_table.dead_letter_queue.id}"
  table_arn  = "${aws_dynamodb_table.dead_letter_queue.arn}"
}

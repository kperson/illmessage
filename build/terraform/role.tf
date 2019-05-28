# Task Role
data "aws_iam_policy_document" "tasks_assume_role_policy_doc" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

data "aws_iam_policy_document" "tasks_role_policy_doc" {
  statement {
    actions = [
      "dynamodb:DescribeStream",
      "dynamodb:GetRecords",
      "dynamodb:GetShardIterator",
    ]

    resources = ["${aws_dynamodb_table.write_ahead_log.stream_arn}"]
  }

  statement {
    actions = [
      "dynamodb:ListStreams",
    ]

    resources = ["*"]
  }

  statement {
    actions = [
      "dynamodb:GetItem",
      "dynamodb:PutItem",
      "dynamodb:BatchWriteItem",
      "dynamodb:BatchGetItem",
      "dynamodb:DeleteItem",
      "dynamodb:Scan",
      "dynamodb:Query",
    ]

    resources = [
      "${aws_dynamodb_table.mailbox.arn}",
      "${aws_dynamodb_table.write_ahead_log.arn}",
      "${aws_dynamodb_table.subscriptions.arn}",
      "${aws_dynamodb_table.sub_message_sequence.arn}",
    ]
  }

  statement {
    actions = [
      "logs:*",
    ]

    resources = [
      "*",
    ]
  }

  statement {
    actions = [
      "lambda:InvokeFunction",
    ]

    resources = [
      "arn:aws:lambda:${var.region}:${data.aws_caller_identity.current.account_id}:function:${var.namespace}_*",
    ]
  }

  statement {
    actions = [
      "iam:PassRole",
    ]

    resources = [
      "${aws_iam_role.execution.arn}",
      "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/${var.namespace}_tasks_role",
    ]
  }

  statement {
    actions = [
      "firehose:PutRecord",
      "firehose:PutRecordBatch",
    ]

    resources = [
      "${aws_kinesis_firehose_delivery_stream.archive.arn}",
    ]
  }
}

resource "aws_iam_policy" "tasks_policy" {
  name   = "${var.namespace}_tasks_assume_policy"
  policy = "${data.aws_iam_policy_document.tasks_role_policy_doc.json}"
}

resource "aws_iam_role" "tasks_role" {
  name               = "${var.namespace}_tasks_role"
  assume_role_policy = "${data.aws_iam_policy_document.tasks_assume_role_policy_doc.json}"
}

resource "aws_iam_role_policy_attachment" "tasks_base_policy" {
  role       = "${aws_iam_role.tasks_role.name}"
  policy_arn = "${aws_iam_policy.tasks_policy.arn}"
}

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
    ]

    resources = [
      "${aws_dynamodb_table.dead_letter_queue.arn}",
      "${aws_dynamodb_table.write_ahead_log.arn}",
    ]
  }

  statement {
    actions = [
      "s3:*",
    ]

    resources = [
      "${aws_s3_bucket.subscription.arn}/*",
      "${aws_s3_bucket.subscription.arn}",
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

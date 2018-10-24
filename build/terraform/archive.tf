resource "aws_s3_bucket" "archive" {
  bucket = "${var.namespace}-archive"
}

data "aws_iam_policy_document" "archive_assume_role_policy" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["firehose.amazonaws.com"]
    }
  }
}

data "aws_iam_policy_document" "archive_role_policy" {
  statement {
    effect = "Allow"

    actions = [
      "s3:AbortMultipartUpload",
      "s3:GetBucketLocation",
      "s3:GetObject",
      "s3:ListBucket",
      "s3:ListBucketMultipartUpload",
      "s3:PutObject",
    ]

    resources = [
      "${aws_s3_bucket.archive.arn}/*",
      "${aws_s3_bucket.archive.arn}",
    ]
  }
}

resource "aws_iam_role" "archive" {
  name               = "${var.namespace}_archive"
  assume_role_policy = "${data.aws_iam_policy_document.archive_assume_role_policy.json}"
}

resource "aws_iam_role_policy" "archive" {
  name   = "${var.namespace}_archive"
  role   = "${aws_iam_role.archive.id}"
  policy = "${data.aws_iam_policy_document.archive_role_policy.json}"
}

resource "aws_kinesis_firehose_delivery_stream" "archive" {
  name        = "${var.namespace}_archive"
  destination = "extended_s3"

  extended_s3_configuration {
    role_arn           = "${aws_iam_role.archive.arn}"
    bucket_arn         = "${aws_s3_bucket.archive.arn}"
    prefix             = "added_at=YYYY-MM-DD-HH/"
    buffer_size        = 256
    buffer_interval    = 300
    compression_format = "GZIP"
  }
}

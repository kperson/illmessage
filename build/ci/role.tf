data "aws_iam_policy_document" "codebuild_assume_role_policy" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["codebuild.amazonaws.com"]
    }
  }
}

data "aws_iam_policy_document" "codebuild_role_policy" {
  # Custom


  statement {
    actions = [
      "lambda:*",
    ]

    resources = [
      "*",
    ]
  }

  statement {
    actions = [
      "dynamodb:*",
    ]

    resources = [
      "*",
    ]
  }

  statement {
    actions = [
      "iam:*",
    ]

    resources = [
      "*",
    ]
  }

  statement {
    actions = [
      "application-autoscaling:*",
    ]

    resources = [
      "*",
    ]
  }

  statement {
    actions = [
      "s3:*",
    ]

    resources = [
      "*",
    ]
  }

  statement {
    actions = [
      "apigateway:*",
    ]

    resources = [
      "*",
    ]
  }

  statement {
    effect = "Allow"

    actions = [
      "logs:*",
    ]

    resources = [
      "*",
    ]
  }


  statement {
    actions = [
      "firehose:*",
    ]

    resources = [
      "*",
    ]
  }

  # Code build


  statement {
    effect = "Allow"

    actions = [
      "ec2:*",
    ]

    resources = ["*"]
  }

}

resource "aws_iam_role" "codebuild" {
  name               = "${var.namespace}_codebuild"
  assume_role_policy = "${data.aws_iam_policy_document.codebuild_assume_role_policy.json}"
}

resource "aws_iam_role_policy" "codebuild" {
  name   = "${var.namespace}_codebuild"
  role   = "${aws_iam_role.codebuild.id}"
  policy = "${data.aws_iam_policy_document.codebuild_role_policy.json}"
}

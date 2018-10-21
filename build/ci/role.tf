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
      "lambda:CreateFunction",
      "lambda:CreateEventSourceMapping",
      "lambda:GetEventSourceMapping",
      "lambda:ListEventSourceMappings",
      "lambda:UpdateEventSourceMapping",
    ]

    resources = [
      "*",
    ]
  }

  statement {
    actions = [
      "lambda:*",
    ]

    resources = [
      "arn:aws:lambda:${var.region}:${data.aws_caller_identity.current.account_id}:function:${var.namespace}_*",
    ]
  }

  statement {
    actions = [
      "dynamodb:*",
    ]

    resources = [
      "arn:aws:dynamodb:${var.region}:${data.aws_caller_identity.current.account_id}:table/${var.namespace}_*",
    ]
  }

  statement {
    actions = [
      "iam:*",
    ]

    resources = [
      "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/${var.namespace}_*",
    ]
  }

  statement {
    actions = [
      "iam:*",
    ]

    resources = [
      "arn:aws:iam::${data.aws_caller_identity.current.account_id}:policy/${var.namespace}_*",
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
      "arn:aws:s3:::${var.namespace}-*",
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
      "arn:aws:logs:${var.region}:${data.aws_caller_identity.current.account_id}:log-group:${var.namespace}:*",
      "arn:aws:logs:${var.region}:${data.aws_caller_identity.current.account_id}:log-group:${var.namespace}",
    ]
  }



  # Code build

  statement {
    actions = [
      "s3:*",
    ]

    resources = [
      "${aws_s3_bucket.state_bucket.arn}/*",
      "${aws_s3_bucket.state_bucket.arn}",
    ]
  }

  statement {
    effect = "Allow"

    actions = [
      "logs:CreateLogGroup",
      "logs:CreateLogStream",
      "logs:PutLogEvents",
    ]

    resources = [
      "arn:aws:logs:${var.region}:${data.aws_caller_identity.current.account_id}:log-group:/aws/codebuild/${var.namespace}_codebuild",
      "arn:aws:logs:${var.region}:${data.aws_caller_identity.current.account_id}:log-group:/aws/codebuild/${var.namespace}_codebuild:*",
    ]
  }

  statement {
    effect = "Allow"

    actions = [
      "logs:DescribeLogGroups",
    ]

    resources = [
      "*",
    ]
  }

  statement {
    effect = "Allow"

    actions = [
      "ec2:CreateNetworkInterface",
      "ec2:DescribeDhcpOptions",
      "ec2:DescribeNetworkInterfaces",
      "ec2:DeleteNetworkInterface",
      "ec2:DescribeSubnets",
      "ec2:DescribeSecurityGroups",
      "ec2:DescribeVpcs",
    ]

    resources = ["*"]
  }

  statement {
    effect = "Allow"

    actions = [
      "ec2:CreateNetworkInterfacePermission",
    ]

    resources = ["arn:aws:ec2:${var.region}:${data.aws_caller_identity.current.account_id}:network-interface/*"]
  }

  statement {
    effect = "Allow"

    actions = [
      "ecr:GetAuthorizationToken",
      "ecr:DescribeRepositories",
      "ecr:CreateRepository",
    ]

    resources = ["*"]
  }

  statement {
    effect = "Allow"

    actions = [
      "ecr:*",
    ]
    
    resources = ["arn:aws:ecr:${var.region}:${data.aws_caller_identity.current.account_id}:repository/${var.namespace}_*"]
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

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
      "iam:CreateRole",
      "iam:DeleteRole",
      "iam:DeleteRolePolicy",
      "iam:GetRolePolicy",
      "iam:ListAttachedRolePolicies",
      "iam:ListRolePolicies",
      "iam:PutRolePolicy",
      "iam:UpdateAssumeRolePolicy",
      "iam:UpdateRole",
      "iam:UpdateRoleDescription",
    ]

    resources = [
      "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/${var.namespace}_",
    ]
  }

  statement {
    actions = [
      "iam:CreatePolicy",
      "iam:CreatePolicyVersion",
      "iam:DeletePolicy",
      "iam:DeletePolicyVersion",
      "iam:GetPolicy",
      "iam:GetPolicyVersion",
      "iam:ListPolicyVersions",
      "iam:SetDefaultPolicyVersion",
    ]

    resources = [
      "arn:aws:iam::${data.aws_caller_identity.current.account_id}:policy/${var.namespace}_*",
    ]
  }

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

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
      "dynamodb:CreateTable",
    ]

    resources = [
      "	arn:aws:dynamodb:${var.region}:${data.aws_caller_identity.current.account_id}:table/${var.namespace}*",
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
      "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/${var.namespace}*",
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
      "arn:aws:iam::${data.aws_caller_identity.current.account_id}:policy/${var.namespace}*",
    ]
  }

  statement {
    actions = [
      "s3:getObject",
      "s3:putObject",
    ]

    resources = [
      "${aws_s3_bucket.state_bucket.arn}/illmessage.tf",
    ]
  }

  statement {
    effect = "Allow"

    actions = [
      "logs:*",
    ]

    resources = ["*"]
  }

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

variable "table_name" {}
variable "table_arn" {}


variable "namespace" {}

variable "max_capacity" {
  default = 10000
}

data "aws_iam_policy_document" "scale_policy_doc" {
  statement {
    actions = [
      "dynamodb:DescribeTable",
      "dynamodb:UpdateTable",
    ]

    resources = [
      "${var.table_arn}",
    ]
  }

  statement {
    actions = [
      "cloudwatch:PutMetricAlarm",
      "cloudwatch:DescribeAlarms",
      "cloudwatch:GetMetricStatistics",
      "cloudwatch:SetAlarmState",
      "cloudwatch:DeleteAlarms",
    ]

    resources = [
      "*",
    ]
  }
}

data "aws_iam_policy_document" "assume_scale_policy_doc" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["application-autoscaling.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "scale_role" {
  name               = "${var.namespace}_dynamodb_scale_role_${var.table_name}"
  assume_role_policy = "${data.aws_iam_policy_document.assume_scale_policy_doc.json}"
}

resource "aws_iam_role_policy" "scale_policy" {
  name   = "${var.namespace}_dynamodb_scale_policy_${var.table_name}"
  role   = "${aws_iam_role.scale_role.id}"
  policy = "${data.aws_iam_policy_document.scale_policy_doc.json}"
}

resource "aws_appautoscaling_target" "read_target" {
  max_capacity       = "${var.max_capacity}"
  min_capacity       = 3
  resource_id        = "table/${var.table_name}"
  role_arn           = "${aws_iam_role.scale_role.arn}"
  scalable_dimension = "dynamodb:table:ReadCapacityUnits"
  service_namespace  = "dynamodb"
}

resource "aws_appautoscaling_policy" "ready_polciy" {
  name               = "DynamoDBReadCapacityUtilization:${aws_appautoscaling_target.read_target.resource_id}"
  policy_type        = "TargetTrackingScaling"
  resource_id        = "${aws_appautoscaling_target.read_target.resource_id}"
  scalable_dimension = "${aws_appautoscaling_target.read_target.scalable_dimension}"
  service_namespace  = "${aws_appautoscaling_target.read_target.service_namespace}"

  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "DynamoDBReadCapacityUtilization"
    }

    target_value       = 70
    scale_in_cooldown  = 60
    scale_out_cooldown = 60
  }
}

resource "aws_appautoscaling_target" "write_target" {
  max_capacity       = "${var.max_capacity}"
  min_capacity       = 5
  resource_id        = "table/${var.table_name}"
  role_arn           = "${aws_iam_role.scale_role.arn}"
  scalable_dimension = "dynamodb:table:WriteCapacityUnits"
  service_namespace  = "dynamodb"
}

resource "aws_appautoscaling_policy" "write_policy" {
  name               = "DynamoDBWriteCapacityUtilization:${aws_appautoscaling_target.write_target.resource_id}"
  policy_type        = "TargetTrackingScaling"
  resource_id        = "${aws_appautoscaling_target.write_target.resource_id}"
  scalable_dimension = "${aws_appautoscaling_target.write_target.scalable_dimension}"
  service_namespace  = "${aws_appautoscaling_target.write_target.service_namespace}"

  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "DynamoDBWriteCapacityUtilization"
    }

    target_value       = 70
    scale_in_cooldown  = 60
    scale_out_cooldown = 60
  }
}

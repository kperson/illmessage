resource "aws_ecs_cluster" "illmessage" {
  name = "${var.namespace}"
}

resource "aws_cloudwatch_log_group" "log" {
  name = "${var.namespace}"
}

data "template_file" "api_container_definitions" {
  template = "${file("files/container-definition.tpl")}"

  vars {
    api_image          = "${jsonencode(aws_ecr_repository.repo.repository_url)}"
    dead_letter_table  = "${jsonencode(aws_dynamodb_table.dead_letter_queue.id)}"
    wal_table          = "${jsonencode(aws_dynamodb_table.write_ahead_log.id)}"
    subscription_table = "${jsonencode(aws_dynamodb_table.subscriptions.id)}"
    cpu                = "512"
    memory             = "1024"
    region             = "${jsonencode(var.region)}"
    log_group          = "${jsonencode(var.namespace)}"
    docker_image       = "${jsonencode(aws_ecr_repository.repo.repository_url)}"
  }
}

resource "aws_ecs_task_definition" "background" {
  depends_on               = ["aws_iam_role_policy_attachment.tasks_base_policy", "aws_iam_policy.tasks_policy"]
  family                   = "${var.namespace}_background"
  network_mode             = "awsvpc"
  requires_compatibilities = ["EC2", "FARGATE"]
  task_role_arn            = "${aws_iam_role.tasks_role.arn}"
  container_definitions    = "${data.template_file.api_container_definitions.rendered}"
}

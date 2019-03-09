module "extract_jar" {
  source         = "../modules/docker-extract"
  container_file = "/jars/app-assembly-1.0.0.jar"
  output_file    = "out.jar"
  tag            = "${module.docker_build.docker_tag}"
}

resource "aws_lambda_function" "api" {
  filename         = "${module.extract_jar.output_file}"
  function_name    = "${var.namespace}_api"
  role             = "${aws_iam_role.tasks_role.arn}"
  handler          = "com.github.kperson.api.LambdaAPI"
  runtime          = "java8"
  memory_size      = 512
  timeout          = 20
  publish          = true
  source_code_hash = "${base64sha256(file(module.extract_jar.output_file))}"

  environment {
    variables = {
      MAILBOX_TABLE       = "${aws_dynamodb_table.mailbox.id}"
      WAL_TABLE           = "${aws_dynamodb_table.write_ahead_log.id}"
      SUBSCRIPTION_TABLE  = "${aws_dynamodb_table.subscriptions.id}"
      REGION              = "${var.region}"
      BACKGROUND_TASK_ARN = "${aws_ecs_task_definition.background.arn}"
      LOG_LEVEL           = "INFO"
      TASK_SUBNET         = "${var.task_subnet}"
      TASK_SECURITY_GROUP = "${var.task_security_group}"
    }
  }
}

resource "aws_lambda_function" "wal_processor" {
  filename         = "${module.extract_jar.output_file}"
  function_name    = "${var.namespace}_wal_processor"
  role             = "${aws_iam_role.tasks_role.arn}"
  handler          = "com.github.kperson.wal.WriteAheadStreamProcessorImpl"
  runtime          = "java8"
  memory_size      = 512
  timeout          = 360
  publish          = true
  source_code_hash = "${base64sha256(file(module.extract_jar.output_file))}"

  environment {
    variables = {
      MAILBOX_TABLE       = "${aws_dynamodb_table.mailbox.id}"
      WAL_TABLE           = "${aws_dynamodb_table.write_ahead_log.id}"
      SUBSCRIPTION_TABLE  = "${aws_dynamodb_table.subscriptions.id}"
      REGION              = "${var.region}"
      BACKGROUND_TASK_ARN = "${aws_ecs_task_definition.background.arn}"
      LOG_LEVEL           = "INFO"
      TASK_SUBNET         = "${var.task_subnet}"
      TASK_SECURITY_GROUP = "${var.task_security_group}"
    }
  }
}

resource "aws_lambda_event_source_mapping" "processor" {
  batch_size        = 100
  event_source_arn  = "${aws_dynamodb_table.write_ahead_log.stream_arn}"
  enabled           = true
  function_name     = "${aws_lambda_function.processor.arn}"
  starting_position = "LATEST"
}

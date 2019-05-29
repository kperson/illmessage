locals {
  app_envs = {
    MAILBOX_TABLE              = "${aws_dynamodb_table.mailbox.id}"
    WAL_TABLE                  = "${aws_dynamodb_table.write_ahead_log.id}"
    SUBSCRIPTION_TABLE         = "${aws_dynamodb_table.subscriptions.id}"
    SUBSCRIPTION_MESSAGE_TABLE = "${aws_dynamodb_table.sub_message_sequence.id}"
    REGION                     = "${var.region}"
    LOG_LEVEL                  = "INFO"
  }
}

module "docker_build" {
  source      = "../modules/docker-build"
  working_dir = "../.."
  docker_file = "Dockerfile"
}

module "extract_jar" {
  source         = "../modules/docker-extract"
  container_file = "/jars/app-assembly-1.0.0.jar"
  output_file    = "out.jar"
  tag            = "${module.docker_build.docker_tag}"
  dind_mount     = "${var.dind_mount}"
}

module "api" {
  source        = "../modules/lambda-http-api"
  name          = "${var.namespace}"
  stage_name    = "prod"
  account_id    = "${data.aws_caller_identity.current.account_id}"
  code_filename = "${module.extract_jar.output_file}"
  handler       = "com.github.kperson.api.LambdaAPI"
  role          = "${aws_iam_role.tasks_role.arn}"
  env           = "${local.app_envs}"
}

module "wal_processor" {
  source        = "../modules/dynamo-stream-lambda"
  stream_arn    = "${aws_dynamodb_table.write_ahead_log.stream_arn}"
  function_name = "${var.namespace}_wal_processor"
  code_filename = "${module.extract_jar.output_file}"
  handler       = "com.github.kperson.wal.WriteAheadStreamProcessorImpl"
  role          = "${aws_iam_role.tasks_role.arn}"
  env           = "${local.app_envs}"
}

module "delivery_processor" {
  source        = "../modules/dynamo-stream-lambda"
  stream_arn    = "${aws_dynamodb_table.mailbox.stream_arn}"
  function_name = "${var.namespace}_delivery_processor"
  code_filename = "${module.extract_jar.output_file}"
  handler       = "com.github.kperson.delivery.DeliveryStreamProcessorImpl"
  role          = "${aws_iam_role.tasks_role.arn}"
  env           = "${local.app_envs}"
}

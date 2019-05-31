resource "aws_api_gateway_rest_api" "api" {
  name = "${var.namespace}"

  endpoint_configuration {
    types = ["REGIONAL"]
  }
}

locals {
  app_envs = {
    MAILBOX_TABLE              = "${aws_dynamodb_table.mailbox.id}"
    WAL_TABLE                  = "${aws_dynamodb_table.write_ahead_log.id}"
    SUBSCRIPTION_TABLE         = "${aws_dynamodb_table.subscriptions.id}"
    SUBSCRIPTION_MESSAGE_TABLE = "${aws_dynamodb_table.sub_message_sequence.id}"
    CF_REGISTRATION_TABLE      = "${aws_dynamodb_table.cf_registration.id}"
    REGION                     = "${var.region}"
    ACCOUNT_ID                 = "${data.aws_caller_identity.current.account_id}"
    LOG_LEVEL                  = "INFO"
    API_ENDPOINT               = "https://${aws_api_gateway_rest_api.api.id}.execute-api.${var.region}.amazonaws.com/illmessage"
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
  source               = "../modules/lambda-http-api-external"
  name                 = "${var.namespace}"
  stage_name           = "illmessage"
  account_id           = "${data.aws_caller_identity.current.account_id}"
  code_filename        = "${module.extract_jar.output_file}"
  handler              = "com.github.kperson.api.LambdaAPI"
  role                 = "${data.template_file.role_completion.rendered}"
  env                  = "${local.app_envs}"
  api_id               = "${aws_api_gateway_rest_api.api.id}"
  api_root_resource_id = "${aws_api_gateway_rest_api.api.root_resource_id}"
}

module "wal_processor" {
  source        = "../modules/dynamo-stream-lambda"
  stream_arn    = "${aws_dynamodb_table.write_ahead_log.stream_arn}"
  function_name = "${var.namespace}_wal_processor"
  code_filename = "${module.extract_jar.output_file}"
  handler       = "com.github.kperson.wal.WriteAheadStreamProcessorImpl"
  role          = "${data.template_file.role_completion.rendered}"
  env           = "${local.app_envs}"
}

module "delivery_processor" {
  source        = "../modules/dynamo-stream-lambda"
  stream_arn    = "${aws_dynamodb_table.mailbox.stream_arn}"
  function_name = "${var.namespace}_delivery_processor"
  code_filename = "${module.extract_jar.output_file}"
  handler       = "com.github.kperson.delivery.DeliveryStreamProcessorImpl"
  role          = "${data.template_file.role_completion.rendered}"
  env           = "${local.app_envs}"
}

resource "aws_lambda_function" "cloudformation" {
  filename         = "${module.extract_jar.output_file}"
  function_name    = "${var.namespace}_cloudformation"
  role             = "${data.template_file.role_completion.rendered}"
  handler          = "com.github.kperson.cf.RegisterHandlerImpl"
  runtime          = "java8"
  memory_size      = 512
  timeout          = 900
  publish          = true
  source_code_hash = "${filesha256(module.extract_jar.output_file)}"

  environment {
    variables = "${local.app_envs}"
  }
}

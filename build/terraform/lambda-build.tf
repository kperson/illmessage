module "docker_build" {
  source      = "../modules/docker-build"
  working_dir = "../.."
  docker_file = "Dockerfile"
}

module "extract_jar" {
  source         = "../modules/docker-extract"
  container_file = "/code/app/target/scala-2.12/app-assembly-1.0.0.jar"
  output_file    = "out.jar"
  tag            = "${module.docker_build.docker_tag}"
}

resource "aws_lambda_function" "api" {
  filename         = "${module.extract_jar.output_file}"
  function_name    = "${var.namespace}_api"
  role             = "${aws_iam_role.tasks_role.arn}"
  handler          = "com.github.kperson.app.LambdaAPIInit"
  runtime          = "java8"
  memory_size      = 512
  timeout          = 20
  publish          = true
  source_code_hash = "${base64sha256(file(module.extract_jar.output_file))}"

  environment {
    variables = {
      DEAD_LETTER_TABLE = "${aws_dynamodb_table.dead_letter_queue.id}"
      WAL_TABLE         = "${aws_dynamodb_table.write_ahead_log.id}"
      AWS_BUCKET        = "${aws_s3_bucket.subscription.id}"
      REGION            = "${var.region}"
      LOG_LEVEL         = "INFO"
    }
  }
}

resource "aws_lambda_function" "processor" {
  filename         = "${module.extract_jar.output_file}"
  function_name    = "${var.namespace}_processor"
  role             = "${aws_iam_role.tasks_role.arn}"
  handler          = "com.github.kperson.app.MessageProcessor"
  runtime          = "java8"
  memory_size      = 512
  timeout          = 20
  publish          = true
  source_code_hash = "${base64sha256(file(module.extract_jar.output_file))}"

  environment {
    variables = {
      DEAD_LETTER_TABLE = "${aws_dynamodb_table.dead_letter_queue.id}"
      WAL_TABLE         = "${aws_dynamodb_table.write_ahead_log.id}"
      AWS_BUCKET        = "${aws_s3_bucket.subscription.id}"
      REGION            = "${var.region}"
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

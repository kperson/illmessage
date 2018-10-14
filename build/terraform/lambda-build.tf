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
  handler          = "com.github.kperson.app.LambdaInit"
  runtime          = "java8"
  memory_size      = 512
  timeout          = 20
  publish          = true
  source_code_hash = "${base64sha256(file(module.extract_jar.output_file))}"

  environment {
    variables = {
      DEAD_LETTER_TABLE = "${aws_dynamodb_table.dead_letter_queue.id}"
      WAL_TABLE         = "${aws_dynamodb_table.write_ahead_log.id}"
      AWS_BUCKET        = "TODO"
      REGION            = "${var.region}"
    }
  }
}
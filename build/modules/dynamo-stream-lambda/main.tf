variable "stream_arn" {
  type = "string"
}

variable "function_name" {
  type = "string"
}

variable "code_filename" {
  type = "string"
}

variable "handler" {
  type = "string"
}

variable "role" {
  type = "string"
}

variable "env" {
  type = "map"
}

variable "runtime" {
  type    = "string"
  default = "java8"
}

variable "layers" {
  default = [

  ]
}

variable "memory_size" {
  type    = "string"
  default = "512"
}

variable "timeout" {
  type    = "string"
  default = "360"
}

resource "aws_lambda_function" "lambda" {
  filename         = "${var.code_filename}"
  function_name    = "${var.function_name}"
  role             = "${var.role}"
  handler          = "${var.handler}"
  runtime          = "${var.runtime}"
  memory_size      = "${var.memory_size}"
  timeout          = "${var.timeout}"
  publish          = true
  source_code_hash = "${filesha256(var.code_filename)}"
  layers           = "${var.layers}"

  environment {
    variables = "${var.env}"
  }
}

resource "aws_lambda_event_source_mapping" "lambda" {
  batch_size        = 100
  event_source_arn  = "${var.stream_arn}"
  enabled           = true
  function_name     = "${aws_lambda_function.lambda.arn}"
  starting_position = "LATEST"
}

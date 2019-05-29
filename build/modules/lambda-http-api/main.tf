variable "name" {
    type = "string"
}

variable "region" {
    type = "string"
    default="us-east-1"
}

variable "stage_name" {
    type = "string"
}

variable "account_id" {
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

variable "memory_size" {
  type    = "string"
  default = "512"
}

variable "timeout" {
  type    = "string"
  default = "30"
}


resource "random_string" "lambda_suffix" {
  length  = 15
  upper   = false
  number  = false
  special = false
}

resource "aws_api_gateway_rest_api" "api" {
  name = "${var.name}"

  endpoint_configuration {
    types = ["REGIONAL"]
  }
}

resource "aws_lambda_function" "api" {
  filename         = "${var.code_filename}"
  function_name    = "${var.name}_api_${random_string.lambda_suffix.result}"
  role             = "${var.role}"
  handler          = "${var.handler}"
  runtime          = "${var.runtime}"
  memory_size      = "${var.memory_size}"
  timeout          = "${var.timeout}"
  publish          = true
  source_code_hash = "${base64sha256(file(var.code_filename))}"

  environment {
    variables = "${var.env}"
  }
}

resource "aws_api_gateway_resource" "api" {
  rest_api_id = "${aws_api_gateway_rest_api.api.id}"
  parent_id   = "${aws_api_gateway_rest_api.api.root_resource_id}"
  path_part   = "{proxy+}"
}

resource "aws_api_gateway_method" "api" {
  rest_api_id   = "${aws_api_gateway_rest_api.api.id}"
  resource_id   = "${aws_api_gateway_resource.api.id}"
  http_method   = "ANY"
  authorization = "NONE"
}

resource "aws_api_gateway_integration" "api" {
  rest_api_id             = "${aws_api_gateway_rest_api.api.id}"
  resource_id             = "${aws_api_gateway_resource.api.id}"
  http_method             = "${aws_api_gateway_method.api.http_method}"
  integration_http_method = "POST"
  type                    = "AWS_PROXY"
  uri                     = "arn:aws:apigateway:${var.region}:lambda:path/2015-03-31/functions/${aws_lambda_function.api.arn}/invocations"
}

resource "aws_lambda_permission" "api" {
  statement_id  = "AllowExecutionFromAPIGateway"
  action        = "lambda:InvokeFunction"
  function_name = "${aws_lambda_function.api.arn}"
  principal     = "apigateway.amazonaws.com"
  source_arn    = "arn:aws:execute-api:${var.region}:${var.account_id}:${aws_api_gateway_rest_api.api.id}/*/${aws_api_gateway_method.api.http_method}${aws_api_gateway_resource.api.path}"
}

resource "aws_api_gateway_deployment" "api" {
  depends_on  = ["aws_api_gateway_integration.api"]
  rest_api_id = "${aws_api_gateway_rest_api.api.id}"
  stage_name  = "${var.stage_name}"
  stage_name  = "prod"
}

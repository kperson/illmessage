# resource "aws_api_gateway_rest_api" "api" {
#   name = "${var.namespace}"

#   endpoint_configuration {
#     types = ["REGIONAL"]
#   }
# }

# resource "aws_api_gateway_resource" "api" {
#   rest_api_id = "${aws_api_gateway_rest_api.api.id}"
#   parent_id   = "${aws_api_gateway_rest_api.api.root_resource_id}"
#   path_part   = "{proxy+}"
# }

# resource "aws_api_gateway_method" "api" {
#   rest_api_id   = "${aws_api_gateway_rest_api.api.id}"
#   resource_id   = "${aws_api_gateway_resource.api.id}"
#   http_method   = "ANY"
#   authorization = "NONE"
# }

# resource "aws_api_gateway_integration" "api" {
#   rest_api_id             = "${aws_api_gateway_rest_api.api.id}"
#   resource_id             = "${aws_api_gateway_resource.api.id}"
#   http_method             = "${aws_api_gateway_method.api.http_method}"
#   integration_http_method = "POST"
#   type                    = "AWS_PROXY"
#   uri                     = "arn:aws:apigateway:${var.region}:lambda:path/2015-03-31/functions/${aws_lambda_function.api.arn}/invocations"
# }

# resource "aws_lambda_permission" "api" {
#   statement_id  = "AllowExecutionFromAPIGateway"
#   action        = "lambda:InvokeFunction"
#   function_name = "${aws_lambda_function.api.arn}"
#   principal     = "apigateway.amazonaws.com"
#   source_arn    = "arn:aws:execute-api:${var.region}:${data.aws_caller_identity.current.account_id}:${aws_api_gateway_rest_api.api.id}/*/${aws_api_gateway_method.api.http_method}${aws_api_gateway_resource.api.path}"
# }

# resource "aws_api_gateway_deployment" "api" {
#   depends_on  = ["aws_api_gateway_integration.api"]
#   rest_api_id = "${aws_api_gateway_rest_api.api.id}"
#   stage_name  = "prod"
# }

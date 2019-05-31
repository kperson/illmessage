output "cloudformation_service_token" {
  value = "${aws_lambda_function.cloudformation.arn}"
}
output "api_endpoint" {
  value = "https://${aws_api_gateway_rest_api.api.id}.execute-api.${var.region}.amazonaws.com/illmessage"
}


# https://docs.aws.amazon.com/apigateway/latest/developerguide/apigateway-resource-policies-examples.html#apigateway-resource-policies-cross-account-example
output "api_execution_arn" {
  value = "${aws_api_gateway_rest_api.api.execution_arn}"
}

output "state_storage_bucket" {
  value = "value"
}

resource "aws_cloudformation_stack" "subscriptions" {
    name = "OutPuts${var.namespace}"
    template_body = "${templatefile("export.tmpl", { service_token = aws_lambda_function.cloudformation.arn, api_endpoint = "https://${aws_api_gateway_rest_api.api.id}.execute-api.${var.region}.amazonaws.com/illmessage", api_execution_arn = aws_api_gateway_rest_api.api.execution_arn, namespace = var.namespace })}"
}

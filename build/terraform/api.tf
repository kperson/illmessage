# resource "aws_api_gateway_rest_api" "api" {
#   name        = "${var.namespace}"

#   endpoint_configuration {
#     types = ["REGIONAL"]
#   }
# }
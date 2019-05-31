variable "subscriptions" {
}

variable "service_token" {
}

resource "random_string" "cloudformation_stack_name" {
  length  = 15
  upper   = false
  number  = false
  special = false
}
resource "aws_cloudformation_stack" "subscriptions" {
    name = "${random_string.cloudformation_stack_name.result}"
    template_body = "${templatefile("${path.module}/cf.tmpl", { subscriptions = var.subscriptions, service_token = var.service_token })}"
}

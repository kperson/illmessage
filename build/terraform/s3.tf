resource "aws_s3_bucket" "subscription" {
  bucket        = "${var.namespace}-subscription"
  force_destroy = true
}

resource "aws_s3_bucket" "subscription" {
  bucket        = "${var.namespace}_subscription"
  force_destroy = true
}

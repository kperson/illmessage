variable "bucket_name" {
}


resource "random_string" "state_bucket" {
  length  = 15
  upper   = false
  number  = false
  special = false
}
resource "aws_s3_bucket" "state_bucket" {
  bucket = "${var.bucket_name}"
}

output "bucket_id" {
  value = "${aws_s3_bucket.state_bucket.id}"
}

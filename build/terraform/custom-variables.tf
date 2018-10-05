variable "namespace" {
  default = "illmessage"
}
variable "region" {
  default = "us-east-1"
}

variable "profile" {
  default = "default"
}

provider "aws" {
  region  = "${var.region}"
  profile = "${var.profile}"
}
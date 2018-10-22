variable "namespace" {
  default = "illmessage"
}

variable "region" {
  default = "us-east-1"
}

variable "profile" {
  default = "default"
}

variable "vpc_id" {}

variable "task_security_group" {}

variable "task_subnet" {}

provider "aws" {
  region  = "${var.region}"
  profile = "${var.profile}"
}

data "aws_caller_identity" "current" {}

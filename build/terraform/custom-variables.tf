variable "namespace" {
  default = "illmessage"
}

variable "region" {
  default = "us-east-1"
}

variable "profile" {
  default = "default"
}

variable "allowed_queues" {
  default = "*"
}

variable "dind_mount" {
  default = "-v /var/run/docker.sock:/var/run/docker.sock"
}

provider "aws" {
  region  = "${var.region}"
  profile = "${var.profile}"
}

data "aws_caller_identity" "current" {}

variable "namespace" {
  default = "illmessage"
  type    = "string"
}

variable "region" {
  default = "us-east-1"
  type    = "string"
}

variable "profile" {
  type = "string"
}

variable "git_repo" {
  default = "https://github.com/kperson/illmessage.git"
  type    = "string"
}

variable "build_vpc_id" {
  type = "string"
}

variable "build_subnets" {
  type = "list"
}

variable "build_security_group_ids" {
  type = "list"
}

provider "aws" {
  region  = "${var.region}"
  profile = "${var.profile}"
}

data "aws_caller_identity" "current" {}

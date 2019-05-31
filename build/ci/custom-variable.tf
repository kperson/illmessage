variable "namespace" {
  type    = "string"
}

variable "region" {
  default = "us-east-1"
  type    = "string"
}

variable "profile" {
  type = "string"
  default = "default"
}

variable "git_repo" {
  default = "https://github.com/kperson/illmessage.git"
  type    = "string"
}

provider "aws" {
  region  = "${var.region}"
  profile = "${var.profile}"
}

variable "bucket_id" {
  
}


data "aws_caller_identity" "current" {}

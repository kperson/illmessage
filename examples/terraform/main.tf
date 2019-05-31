provider "aws" {
  region  = "us-east-1"
  profile = "default"
}

data "terraform_remote_state" "illmessage" {
  backend = "s3"
  config = {
    profile = "default"
    bucket  = "illmessage-state-storage-asxdczyccuctrlw"
    region  = "us-east-1"
    key     = "illmessage-build.tf"
  }
}

resource "aws_sqs_queue" "queue_one" {
  name = "queue_1"
}


resource "aws_sqs_queue" "queue_two" {
  name = "queue_2"
}

module "my_subscriptions" {
  source = "github.com/kperson/illmessage//terraform-support"
  subscriptions = [
    {
      name        = "sub_one"
      exchange    = "my_exchange"
      binding_key = "db.my_db._my_table_one.*"
      queue       = "${aws_sqs_queue.queue_one.id}"
    },
    {
      name        = "sub_two"
      exchange    = "my_exchange"
      binding_key = "db.my_db._my_table_two.*"
      queue       = "${aws_sqs_queue.queue_two.id}"
    }
  ]
  service_token = "${data.terraform_remote_state.illmessage.outputs.cloudformation_service_token}"
}

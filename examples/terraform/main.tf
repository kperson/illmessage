
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

module "my_subscriptions" {
  source = "github.com/kperson/illmessage//terraform-support"
  subscriptions = [
    {
      name        = "source_one"
      exchange    = "exchange_one"
      binding_key = "com.db.*"
      queue       = "test_queue"
    },
    {
      name        = "source_two"
      exchange    = "exchange_one"
      binding_key = "com.db.*"
      queue       = "test_queue_2"
    }
  ]
  service_token = "${data.terraform_remote_state.illmessage.outputs.cloudformation_service_token}"
}

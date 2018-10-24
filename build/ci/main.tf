resource "aws_s3_bucket" "state_bucket" {
  bucket = "${var.namespace}-state-storage"
}

resource "aws_codebuild_project" "codebuild" {
  name          = "${var.namespace}_codebuild"
  description   = "illMessage Build"
  build_timeout = 15
  service_role  = "${aws_iam_role.codebuild.arn}"

  artifacts {
    type = "NO_ARTIFACTS"
  }

  environment {
    compute_type    = "BUILD_GENERAL1_SMALL"
    image           = "aws/codebuild/docker:17.09.0"
    type            = "LINUX_CONTAINER"
    privileged_mode = true

    environment_variable {
      "name"  = "NAMESPACE"
      "value" = "${var.namespace}"
    }

    environment_variable {
      "name"  = "BUILD_STATE_BUCKET"
      "value" = "${aws_s3_bucket.state_bucket.id}"
    }

    environment_variable {
      "name"  = "TERRAFORM_ZIP_URL"
      "value" = "https://releases.hashicorp.com/terraform/0.11.8/terraform_0.11.8_linux_amd64.zip"
    }

    environment_variable {
      "name"  = "VPC_ID"
      "value" = "${var.build_vpc_id}"
    }

    environment_variable {
      "name"  = "TASK_SECURITY_GROUP"
      "value" = "${var.build_security_group_ids[0]}"
    }

    environment_variable {
      "name"  = "TASK_SUBNET"
      "value" = "${var.build_subnets[0]}"
    }
  }

  source {
    type            = "GITHUB"
    location        = "${var.git_repo}"
    git_clone_depth = 1
    buildspec       = "build/buildspec.yml"
  }

  vpc_config {
    vpc_id             = "${var.build_vpc_id}"
    security_group_ids = ["${var.build_security_group_ids}"]
    subnets            = ["${var.build_subnets}"]
  }
}

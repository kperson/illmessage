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
      name  = "NAMESPACE"
      value = "${var.namespace}"
    }

    environment_variable {
      name  = "BUILD_STATE_BUCKET"
      value = "${var.bucket_id}"
    }

    environment_variable {
      name  = "TERRAFORM_ZIP_URL"
      value = "https://releases.hashicorp.com/terraform/0.12.0/terraform_0.12.0_linux_amd64.zip"
    }

  }

  source {
    type            = "GITHUB"
    location        = "${var.git_repo}"
    git_clone_depth = 1
    buildspec       = "build/buildspec.yml"
  }

}

resource "aws_ecr_repository" "repo" {
  name = "${var.namespace}_api_repo"
}

resource "aws_ecr_lifecycle_policy" "repo" {
  repository = "${aws_ecr_repository.repo.name}"
  policy     = "${file("files/docker_policy.json")}"
}

resource "random_string" "tag" {
  length  = 15
  upper   = false
  number  = false
  special = false
}

data "template_file" "build_script" {
  template = "${file("files/image_build_push_script.tpl")}"

  vars {
    repo        = "${aws_ecr_repository.repo.repository_url}"
    tag         = "${random_string.tag.result}"
    docker_file = "Dockerfile"
  }
}

resource "null_resource" "docker_build" {
  triggers = {
    time = "${timestamp()}"
  }

  provisioner "local-exec" {
    working_dir = "../../"
    command     = "${data.template_file.build_script.rendered}"

    environment {}
  }
}

# output "docker_tag" {
#   value = "${random_string.tag.result}"
# }


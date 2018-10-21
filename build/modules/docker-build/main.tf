variable "working_dir" {
  type = "string"
}

variable "docker_file" {
  type    = "string"
  default = "Dockerfile"
}

variable "repo_url" {
  type = "string"
}

resource "random_string" "tag" {
  length  = 15
  upper   = false
  number  = false
  special = false
}

data "template_file" "build_script" {
  template = "${file("${path.module}/image_build_push_script.tpl")}"

  vars {
    repo        = "${var.repo_url}"
    tag         = "${random_string.tag.result}"
    docker_file = "${var.docker_file}"
  }
}

resource "null_resource" "docker_build" {
  triggers = {
    time = "${timestamp()}"
  }

  provisioner "local-exec" {
    working_dir = "${var.working_dir}"
    command     = "${data.template_file.build_script.rendered}"

    environment {}
  }
}

output "docker_tag" {
  value = "${random_string.tag.result}"
}

output "repo_url" {
  value = "${var.repo_url}"
}

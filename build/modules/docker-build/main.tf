variable "working_dir" {
  type = "string"
}

variable "docker_file" {
  type    = "string"
  default = "Dockerfile"
}

resource "random_string" "tag" {
  length  = 15
  upper   = false
  number  = false
  special = false
  keepers = {
    time = "${timestamp()}"
  }
}

data "template_file" "build_script" {
  template = "${file("${path.module}/image_build_push_script.tpl")}"

  vars = {
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

    environment = {

    }
  }
}

data "template_file" "docker_tag" {
  depends_on = ["null_resource.docker_build"]
  template   = "$${output_file}"

  vars = {
    output_file = "${random_string.tag.result}"
  }
}

output "docker_tag" {
  value = "${data.template_file.docker_tag.rendered}"
}

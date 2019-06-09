variable "container_file" {
  type = "string"
}

variable "output_extension" {
  type = "string"
}


resource "random_string" "output_file" {
  length  = 15
  upper   = false
  number  = false
  special = false
  keepers = {
    time = "${timestamp()}"
  }
}

variable "tag" {
  type = "string"
}

variable "dind_mount" {
  default = "-v /var/run/docker.sock:/var/run/docker.sock"
}

data "template_file" "build_script" {
  template = "${file("${path.module}/extract_script.tpl")}"

  vars = {
    container_file = "${var.container_file}"
    tag            = "${var.tag}"
    output_file    = "${random_string.output_file.result}.${var.output_extension}"
    out_dir        = "${path.cwd}"
    dind_mount     = "${var.dind_mount}"
  }
}

resource "null_resource" "docker_extract" {
  triggers = {
    time = "${timestamp()}"
  }

  provisioner "local-exec" {
    command = "${data.template_file.build_script.rendered}"

    environment = {

    }
  }
}

data "template_file" "ouput_file" {
  depends_on = ["null_resource.docker_extract"]
  template   = "$${output_file}"

  vars = {
    output_file = "${random_string.output_file.result}.${var.output_extension}"

  }
}

output "output_file" {
  value = "${data.template_file.ouput_file.rendered}"
}

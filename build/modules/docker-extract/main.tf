variable "container_file" {
  type = "string"
}

variable "output_file" {
  type = "string"
}

variable "tag" {
  type = "string"
}

data "template_file" "build_script" {
  template = "${file("${path.module}/extract_script.tpl")}"

  vars {
    container_file = "${var.container_file}"
    tag            = "${var.tag}"
    output_file    = "${var.output_file}"
    out_dir        = "${path.cwd}"
  }
}

resource "null_resource" "docker_extract" {
  triggers = {
    time = "${timestamp()}"
  }

  provisioner "local-exec" {
    command = "${data.template_file.build_script.rendered}"

    environment {}
  }
}

data "template_file" "ouput_file" {
  depends_on = ["null_resource.docker_extract"]
  template   = "$${output_file}"

  vars {
    output_file = "${var.output_file}"
  }
}

output "output_file" {
  value = "${data.template_file.ouput_file.rendered}"
}

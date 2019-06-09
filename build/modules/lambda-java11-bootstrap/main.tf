variable "jar_file" {
  type = "string"
}

resource "random_string" "tag" {
  length  = 15
  upper   = false
  number  = false
  special = false
}

data "template_file" "bootstap_script" {
  template = "${file("${path.module}/bootstrap.sh.tpl")}"

  vars = {
    jar_file = "${var.jar_file}"
  }
}
resource "local_file" "bootstrap" {
  content  = "${data.template_file.bootstap_script.rendered}"
  filename = "bootstrap"
}

resource "null_resource" "zip" {
  triggers = {
    time = "${timestamp()}"
  }

  provisioner "local-exec" {
    command = "chmod +x bootstrap && zip ${random_string.tag.result}.zip bootstrap ${var.jar_file} && sleep 5"
  }
}

data "template_file" "docker_tag" {
  depends_on = ["null_resource.zip"]
  template   = "$${out}"

  vars = {
    out = "${random_string.tag.result}.zip"
  }
}

output "zip_file" {
  value = "${random_string.tag.result}.zip"
}

variable "jar_file" {
  type = "string"
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

resource "null_resource" "prepare_content" {
  depends_on = ["local_file.bootstrap"]
  triggers = {
    time = "${timestamp()}"
  }

  provisioner "local-exec" {
    command = "chmod +x bootstrap && mkdir -p && ${random_string.tag.result} && cp bootstrap ${random_string.tag.result}/ && cp ${var.jar_file} ${random_string.tag.result}/"
  }
}

data "archive_file" "zip" {
  depends_on  = ["null_resource.prepare_content"]
  type        = "zip"
  source_dir  = "${random_string.tag.result}"
  output_path = "${random_string.tag.result}.zip"
}

data "template_file" "docker_tag" {
  depends_on = ["archive_file.zip"]
  template   = "$${out}"

  vars = {
    out = "${random_string.tag.result}.zip"
  }
}

output "zip_file" {
  value = "${random_string.tag.result}.zip"
}

output "zip_file_hash" {
  value = "${data.archive_file.zip.output_base64sha256}"
}

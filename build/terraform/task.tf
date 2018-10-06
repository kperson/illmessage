


# data "template_file" "api_container_definitions" {
#   template = "${file("api_container_definitions.tpl")}"

#   vars {
#     api_image = "${jsonencode(module.api_docker_repo.repo_url)}"
#     kv_table  = "${jsonencode(module.api_storage.table_name)}"
#   }
# }

# resource "aws_ecs_task_definition" "api" {
#   depends_on               = ["module.api_docker_build"]
#   family                   = "${module.api_name.dash}"
#   network_mode             = "awsvpc"
#   requires_compatibilities = ["EC2"]
#   task_role_arn            = "${aws_iam_role.rtmp.arn}"
#   container_definitions    = "${data.template_file.api_container_definitions.rendered}"
# }
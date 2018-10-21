resource "aws_ecr_repository" "repo" {
  name = "${var.namespace}_background"
}

resource "aws_ecr_lifecycle_policy" "repo" {
  repository = "${aws_ecr_repository.repo.name}"
  policy     = "${file("files/docker_policy.json")}"
}

module "docker_build" {
  source      = "../modules/docker-build"
  working_dir = "../.."
  repo_url    = "${aws_ecr_repository.repo.repository_url}"
  docker_file = "Dockerfile"
}

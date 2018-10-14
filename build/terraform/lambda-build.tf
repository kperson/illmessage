module "docker_build" {
  source      = "../modules/docker-build"
  working_dir = "../.."
  docker_file = "Dockerfile"
}

module "extract_jar" {
  source         = "../modules/docker-extract"
  container_file = "/code/app/target/scala-2.12/app-assembly-1.0.0.jar"
  output_file    = "out.jar"
  tag            = "${module.docker_build.docker_tag}"
}
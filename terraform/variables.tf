variable "prefix" {}

variable "owner" {}

variable "aws_az" {
  type = list(string)
}

variable "vpc_name" {}

variable "vpc_cidr" {
  default = "10.0.0.0/16"
}

variable "aws_subnet_public" {
  type = list(string)
}

variable "api_server_ecr_name" {}
variable "gatling_ecs_cluster_name" {}
variable "gatling_s3_log_bucket_name" {}

variable "gatling_runner_ecr_name" {}
variable "gatling_s3_reporter_ecr_name" {}
variable "gatling_aggregate_runner_ecr_name" {}


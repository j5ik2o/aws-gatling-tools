variable "enabled" {
  default = true
}

variable "prefix" {}
variable "owner" {}

variable "gatling_ecs_cluster_name" {}
variable "gatling_s3_log_bucket_name" {}

variable "gatling_runner_ecr_name" {}
variable "gatling_s3_reporter_ecr_name" {}
variable "gatling_aggregate_runner_ecr_name" {}


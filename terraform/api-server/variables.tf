variable "prefix" {}
variable "owner" {}
variable "aws_account_id" {}

variable "vpc_id" {}
variable "subnet_ids" {
  type = list(string)
}
variable "api_server_ecs_cluster_name" {}
variable "api_server_ecr_name" {}

variable "aws_subnet_public" {
  type = list(string)
}

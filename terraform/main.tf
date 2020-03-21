terraform {
  required_version = ">= 0.12"
  backend "s3" {}
}

provider "aws" {
  region  = "${var.aws_region}"
  profile = "${var.aws_profile}"
}

data "aws_availability_zones" "available" {
}

locals {
}

resource "random_string" "suffix" {
  length  = 8
  special = false
}

data "aws_security_group" "default" {
  name   = "default"
  vpc_id = module.vpc.vpc_id
}

module "vpc" {
  source = "terraform-aws-modules/vpc/aws"

  name = "${var.vpc_name}"
  cidr = "${var.vpc_cidr}"

  azs              = data.aws_availability_zones.available.names
  public_subnets   = "${var.aws_subnet_private}"

  enable_dns_hostnames = true
  enable_dns_support   = true

  enable_nat_gateway = true
  enable_vpn_gateway = true

  tags = {
  }

  public_subnet_tags = {
  }

  private_subnet_tags = {
  }
}

module "ecr_api_server" {
  source   = "./ecr"
  prefix   = "${var.prefix}"
  owner    = "${var.owner}"
  enabled  = true
  ecr_name = "j5ik2o/api-server"
}

module "gatling" {
  source     = "./gatling"
  enabled    = true
  aws_region = "${var.aws_region}"
  prefix     = "${var.prefix}"
  owner      = "${var.owner}"

  gatling_ecs_cluster_name          = "${var.gatling_ecs_cluster_name}"
  gatling_runner_ecr_name           = "${var.gatling_runner_ecr_name}"
  gatling_aggregate_runner_ecr_name = "${var.gatling_aggregate_runner_ecr_name}"

  gatling_s3_reporter_ecr_name = "${var.gatling_s3_reporter_ecr_name}"
  gatling_s3_log_bucket_name   = "${var.gatling_s3_log_bucket_name}"

  gatling_dd_api_key = "${var.gatling_dd_api_key}"
}

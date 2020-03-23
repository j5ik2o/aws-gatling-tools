output "api_server_alb_dns" {
  value = module.ecr_api_server.api_server_alb_dns_name
}
output "api_server_ecs_cluster_name" {
  value = module.ecr_api_server.api_server_ecs_cluster_name
}

output "api_server_ecr_repository_arn" {
  value = module.ecr_api_server.api_server_ecr_arn
}

output "api_server_ecr_repository_url" {
  value = module.ecr_api_server.api_server_ecr_repository_url
}

output "gatling_ecs_cluster_name" {
  value = module.gatling.gatling_ecs_cluster_name
}

output "gatling_runner_ecr_arn" {
  value = module.gatling.gatling_runner_ecr_arn
}

output "gatling_runner_ecr_repository_url" {
  value =  module.gatling.gatling_runner_ecr_repository_url
}

output "gatling_s3_reporter_ecr_arn" {
  value =  module.gatling.gatling_s3_reporter_ecr_arn
}

output "gatling_s3_reporter_ecr_repository_url" {
  value =  module.gatling.gatling_s3_reporter_ecr_repository_url
}

output "gatling_aggregate_runner_ecr_arn" {
  value =  module.gatling.gatling_aggregate_runner_ecr_arn
}

output "gatling_aggregate_runner_ecr_repository_url" {
  value =  module.gatling.gatling_aggregate_runner_ecr_repository_url
}

output "gatling_ecs_cluster_name" {
  value = aws_ecs_cluster.ecs_cluster[0].name
}

output "gatling_runner_ecr_arn" {
  value = aws_ecr_repository.gatling_runner_ecr[0].arn
}

output "gatling_runner_ecr_repository_url" {
  value = aws_ecr_repository.gatling_runner_ecr[0].repository_url
}

output "gatling_s3_reporter_ecr_arn" {
  value = aws_ecr_repository.gatling_s3_reporter_ecr[0].arn
}

output "gatling_s3_reporter_ecr_repository_url" {
  value = aws_ecr_repository.gatling_s3_reporter_ecr[0].repository_url
}

output "gatling_aggregate_runner_ecr_arn" {
  value = aws_ecr_repository.gatling_aggregate_runner_ecr[0].arn
}

output "gatling_aggregate_runner_ecr_repository_url" {
  value = aws_ecr_repository.gatling_aggregate_runner_ecr[0].repository_url
}



output "api_server_ecs_cluster_name" {
  value = aws_ecs_cluster.ecs_cluster.name
}

output "api_server_ecr_arn" {
  value = aws_ecr_repository.api_server_ecr.arn
}

output "api_server_ecr_repository_url" {
  value = aws_ecr_repository.api_server_ecr.repository_url
}


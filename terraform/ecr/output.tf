output "ecr_repository_arn" {
  value = aws_ecr_repository.this[0].arn
}

output "ecr_repository_url" {
  value = aws_ecr_repository.this[0].repository_url
}


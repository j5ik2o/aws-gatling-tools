resource "aws_ecr_repository" "api_server_ecr" {
  count = var.enabled ? 1 : 0
  name  = var.api_server_ecr_name
  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_ecr_repository_policy" "api_server_ecr_policy" {
  count  = var.enabled ? 1 : 0
  policy = <<EOF
{
    "Version": "2008-10-17",
    "Statement": [
        {
            "Sid": "api-server-ecr",
            "Effect": "Allow",
            "Principal": "*",
            "Action": [
                "ecr:GetDownloadUrlForLayer",
                "ecr:BatchGetImage",
                "ecr:BatchCheckLayerAvailability",
                "ecr:PutImage",
                "ecr:InitiateLayerUpload",
                "ecr:UploadLayerPart",
                "ecr:CompleteLayerUpload",
                "ecr:DescribeRepositories",
                "ecr:GetRepositoryPolicy",
                "ecr:ListImages",
                "ecr:DeleteRepository",
                "ecr:BatchDeleteImage",
                "ecr:SetRepositoryPolicy",
                "ecr:DeleteRepositoryPolicy"
            ]
        }
    ]
}
EOF

  repository = aws_ecr_repository.api_server_ecr.0.name
  lifecycle {
    create_before_destroy = true
  }
}



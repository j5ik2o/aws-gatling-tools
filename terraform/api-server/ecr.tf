resource "aws_ecr_repository" "api_server_ecr" {
  name  = var.api_server_ecr_name
  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_ecr_repository_policy" "api_server_ecr_policy" {
  policy = <<EOF
{
    "Version": "2008-10-17",
    "Statement": [
        {
            "Sid": "api-server-ecr",
            "Effect": "Allow",
            "Principal": "*",
            "Action": [
                "ecr:*"
            ]
        }
    ]
}
EOF

  repository = aws_ecr_repository.api_server_ecr.name
  lifecycle {
    create_before_destroy = true
  }
}



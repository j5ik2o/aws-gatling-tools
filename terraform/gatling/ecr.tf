resource "aws_ecr_repository" "gatling_runner_ecr" {
  count = "${var.enabled ? 1 : 0}"
  name  = "${var.gatling_runner_ecr_name}"
  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_ecr_repository_policy" "gatling_runner_ecr_policy" {
  count  = "${var.enabled ? 1 : 0}"
  policy = <<EOF
{
    "Version": "2008-10-17",
    "Statement": [
        {
            "Sid": "gaudi-poc-gatling-ecs-runner-ecr",
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

  repository = aws_ecr_repository.gatling_runner_ecr.0.name
  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_ecr_repository" "gatling_s3_reporter_ecr" {
  count = "${var.enabled ? 1 : 0}"
  name = "${var.gatling_s3_reporter_ecr_name}"
  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_ecr_repository_policy" "gatling_s3_reporter_ecr_policy" {
  count = "${var.enabled ? 1 : 0}"
  policy = <<EOF
{
    "Version": "2008-10-17",
    "Statement": [
        {
            "Sid": "thread-weaver-gatling-ecs-s3-reporter-ecr",
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

  repository = aws_ecr_repository.gatling_s3_reporter_ecr.0.name
  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_ecr_repository" "gatling_aggregate_runner_ecr" {
  count = "${var.enabled ? 1 : 0}"
  name = "${var.gatling_aggregate_runner_ecr_name}"
  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_ecr_repository_policy" "gatling_aggregate_runner_ecr_policy" {
  count = "${var.enabled ? 1 : 0}"
  policy = <<EOF
{
    "Version": "2008-10-17",
    "Statement": [
        {
            "Sid": "thread-weaver-gatling-ecs-aggregate-runner-ecr",
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

  repository = aws_ecr_repository.gatling_aggregate_runner_ecr.0.name
  lifecycle {
    create_before_destroy = true
  }
}


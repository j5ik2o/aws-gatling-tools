data "aws_region" "current" {}

resource "aws_ecs_cluster" "ecs_cluster" {
  count = var.enabled ? 1 : 0
  name  = var.api_server_ecs_cluster_name
  tags = {
    Name = var.api_server_ecs_cluster_name
    Owner = var.owner
  }
}

resource "aws_iam_role" "api_server_ecs_task_execution_role" {
  count              = var.enabled ? 1 : 0
  assume_role_policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Service": [
          "ecs-tasks.amazonaws.com"
        ]
      },
      "Action": [
        "sts:AssumeRole"
      ]
    }
  ]
}
EOF
}

//resource "aws_iam_policy" "api_server_ecs_policy" {
//  count = var.enabled ? 1 : 0
//  name = "${var.prefix}-api-server-ecs-policy"
//  path = "/"
//  policy = <<EOF
//{
//  "Version": "2012-10-17",
//  "Statement": [
//    {
//      "Effect": "Allow",
//      "Action": [
//      ],
//      "Resource": [
//      ]
//    },
//    {
//      "Effect": "Allow",
//      "Action": [
//      ],
//      "Resource": "*"
//    }
//  ]
//}
//EOF
//
//}

//resource "aws_iam_role_policy_attachment" "api_server_attach_ec2_policy" {
//  count      = var.enabled ? 1 : 0
//  role       = aws_iam_role.api_server_ecs_task_execution_role[0].name
//  policy_arn = aws_iam_policy.api_server_ecs_policy[0].arn
//}

resource "aws_ecs_task_definition" "api_server" {
  count                    = var.enabled ? 1 : 0
  family                   = "${var.prefix}-api-server"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  task_role_arn            = aws_iam_role.api_server_ecs_task_execution_role[0].arn
  execution_role_arn       = aws_iam_role.api_server_ecs_task_execution_role[0].arn
  cpu                      = "512"
  memory                   = "1024"
  container_definitions    = <<EOF
[
  {
    "name": "gatling-runner",
    "essential": true,
    "image": "${aws_ecr_repository.api_server_ecr[0].repository_url}",
    "environment": [
      { "name": "AWS_REGION", "value": "${data.aws_region.current.name}" }
    ],
    "logConfiguration": {
      "logDriver": "awslogs",
      "options": {
        "awslogs-group":  "${aws_cloudwatch_log_group.gatling_log_group[0].name}",
        "awslogs-region": "ap-northeast-1",
        "awslogs-stream-prefix": "${var.prefix}-gatling-runner"
      }
    }
  }
]
EOF

}

resource "aws_cloudwatch_log_group" "gatling_log_group" {
  count = var.enabled ? 1 : 0
  name  = "/ecs/logs/${var.prefix}-api-server-ecs-group"
}



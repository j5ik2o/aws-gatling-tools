data "aws_region" "current" {}

resource "aws_alb" "api_server_alb" {
  subnets = var.subnet_ids

  security_groups = [
    aws_security_group.api_server_alb.id
  ]

  tags = {
    Owner = var.owner
  }
}

resource "aws_security_group" "api_server_alb" {
  vpc_id = var.vpc_id

  ingress {
    from_port = 80
    to_port = 80
    protocol = "tcp"
    cidr_blocks = [
      "0.0.0.0/0"]
  }

  ingress {
    from_port = 443
    to_port = 443
    protocol = "tcp"
    cidr_blocks = [
      "0.0.0.0/0"]
  }

  egress {
    from_port = 0
    to_port = 0
    protocol = "-1"
    cidr_blocks = [
      "0.0.0.0/0"]
  }

  tags = {
    Owner = var.owner
  }
}

resource "aws_security_group" "api_server_web" {
  vpc_id = var.vpc_id

  ingress {
    from_port = 0
    to_port = 65535
    protocol = "tcp"

    security_groups = [
      aws_security_group.api_server_alb.id
    ]
  }

  egress {
    from_port = 0
    to_port = 0
    protocol = "-1"
    cidr_blocks = [
      "0.0.0.0/0"]
  }

  tags = {
    Owner = var.owner
  }
}

resource "aws_alb_listener" "api_server" {
  load_balancer_arn = aws_alb.api_server_alb.arn
  port = "80"
  protocol = "HTTP"

  default_action {
    target_group_arn = aws_alb_target_group.api_server.id
    type = "forward"
  }

}

resource "aws_alb_target_group" "api_server" {
  vpc_id = var.vpc_id

  deregistration_delay = 10

  health_check {
    healthy_threshold = 2
    unhealthy_threshold = 5
    path = "/hello"
    interval = 60
    timeout = 10
  }

  name = "api-server"
  port = 8080
  protocol = "HTTP"

  target_type = "ip"
  tags = {
    Owner = var.owner
  }
}

resource "aws_ecs_cluster" "ecs_cluster" {
  name = var.api_server_ecs_cluster_name
  tags = {
    Owner = var.owner
  }
}

resource "aws_iam_role" "api_server_ecs" {
  assume_role_policy = <<-EOF
  {
    "Version": "2012-10-17",
    "Statement": [
      {
        "Action": "sts:AssumeRole",
        "Effect": "Allow",
        "Principal": {
          "Service": "ecs.amazonaws.com"
        }
      }
    ]
  }
  EOF
  tags = {
    Owner = var.owner
  }
}

resource "aws_iam_role" "api_server_ecs_execution" {
  assume_role_policy = <<-EOF
  {
    "Version": "2012-10-17",
    "Statement": [
      {
        "Action": "sts:AssumeRole",
        "Effect": "Allow",
        "Principal": {
          "Service": "logs.${data.aws_region.current.name}.amazonaws.com"
        }
      }
    ]
  }
  EOF
  tags = {
    Owner = var.owner
  }
}

resource "aws_iam_role_policy_attachment" "fastladder_ecs_service" {
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonEC2ContainerServiceRole"
  role = aws_iam_role.api_server_ecs.id

}


resource "aws_ecs_task_definition" "api_server" {
  family = "${var.prefix}-api-server"
  requires_compatibilities = [
    "FARGATE"]
  network_mode = "awsvpc"
  execution_role_arn = "arn:aws:iam::${var.aws_account_id}:role/ecsTaskExecutionRole"
  cpu = "512"
  memory = "1024"
  container_definitions = <<-EOF
  [
    {
      "name": "api-server",
      "essential": true,
      "image": "${aws_ecr_repository.api_server_ecr.repository_url}",
      "portMappings": [
        {
          "containerPort": 8080
        }
      ],
      "environment": [
        { "name": "AWS_REGION", "value": "${data.aws_region.current.name}" }
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group":  "${aws_cloudwatch_log_group.api_server_log_group.name}",
          "awslogs-region": "${data.aws_region.current.name}",
          "awslogs-stream-prefix": "${var.prefix}-api-server"
        }
      }
    }
  ]
  EOF
  tags = {
    Owner = var.owner
  }
}

resource "aws_cloudwatch_log_group" "api_server_log_group" {
  name = "/ecs/logs/${var.prefix}-api-server-ecs-group"
  tags = {
    Owner = var.owner
  }
}

resource "aws_ecs_service" "main" {
  name = "api-server"

  depends_on = [
    aws_alb_target_group.api_server]

  cluster = aws_ecs_cluster.ecs_cluster.id

  launch_type = "FARGATE"

  desired_count = "1"

  task_definition = aws_ecs_task_definition.api_server.id

  network_configuration {
    subnets = var.subnet_ids
    security_groups = [
      aws_security_group.api_server_alb.id,
      aws_security_group.api_server_web.id]
    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = aws_alb_target_group.api_server.arn
    container_name = "api-server"
    container_port = "8080"
  }

}


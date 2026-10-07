resource "aws_ecs_cluster" "auth" {
  name = "${var.project_name}-${var.environment}-auth-cluster"

  tags = {
    Name = "${var.project_name}-${var.environment}-auth-cluster"
  }
}

resource "aws_ecs_task_definition" "auth" {
  family                   = "${var.project_name}-${var.environment}-auth"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"

  cpu    = var.ecs_cpu
  memory = var.ecs_memory

  execution_role_arn = aws_iam_role.ecs_execution.arn
  task_role_arn      = aws_iam_role.ecs_task.arn

  container_definitions = jsonencode([
    {
      name      = "auth-service"
      image     = "${aws_ecr_repository.auth.repository_url}:latest"
      essential = true

      portMappings = [
        {
          containerPort = var.auth_container_port
          hostPort      = var.auth_container_port
          protocol      = "tcp"
        }
      ]

      environment = [
        {
          name  = "SPRING_DATASOURCE_URL"
          value = "jdbc:postgresql://${aws_db_instance.auth.address}:5432/${var.db_name}"
        },
        {
          name  = "SPRING_DATASOURCE_USERNAME"
          value = var.db_username
        },
        {
          name  = "SPRING_DATASOURCE_PASSWORD"
          value = var.db_password
        },
        {
          name  = "APP_JWT_SECRET"
          value = var.jwt_secret
        },
        {
          name  = "APP_JWT_ACCESS_TOKEN_EXPIRATION_SECONDS"
          value = "900"
        },
        {
          name  = "APP_JWT_REFRESH_TOKEN_EXPIRATION_SECONDS"
          value = "604800"
        }
      ]

      logConfiguration = {
        logDriver = "awslogs"

        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.auth.name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "auth"
        }
      }
    }
  ])

  tags = {
    Name = "${var.project_name}-${var.environment}-auth-task"
  }
}

resource "aws_ecs_service" "auth" {
  name            = "${var.project_name}-${var.environment}-auth-service"
  cluster         = aws_ecs_cluster.auth.id
  task_definition = aws_ecs_task_definition.auth.arn

  desired_count = var.desired_count
  launch_type   = "FARGATE"

  network_configuration {
    subnets          = aws_subnet.public[*].id
    security_groups  = [aws_security_group.ecs_auth.id]
    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.auth.arn
    container_name   = "auth-service"
    container_port   = var.auth_container_port
  }

  depends_on = [
    aws_lb_listener.auth_http,
    aws_iam_role_policy_attachment.ecs_execution
  ]

  tags = {
    Name = "${var.project_name}-${var.environment}-auth-service"
  }
}

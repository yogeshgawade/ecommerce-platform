resource "aws_security_group" "alb" {
  name        = "${var.project_name}-${var.environment}-auth-alb-sg"
  description = "Security group for the Auth Service Application Load Balancer"
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "HTTP from the internet"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    description = "Allow outbound traffic"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.project_name}-${var.environment}-auth-alb-sg"
  }
}

resource "aws_security_group" "ecs_auth" {
  name        = "${var.project_name}-${var.environment}-auth-ecs-sg"
  description = "Security group for the Auth Service ECS tasks"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "Auth Service traffic from ALB"
    from_port       = var.auth_container_port
    to_port         = var.auth_container_port
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  egress {
    description = "Allow outbound traffic"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.project_name}-${var.environment}-auth-ecs-sg"
  }
}

resource "aws_security_group" "rds" {
  name        = "${var.project_name}-${var.environment}-auth-rds-sg"
  description = "Security group for the Auth Service PostgreSQL database"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "PostgreSQL traffic from Auth Service ECS tasks"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs_auth.id]
  }

  egress {
    description = "Allow outbound traffic"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.project_name}-${var.environment}-auth-rds-sg"
  }
}

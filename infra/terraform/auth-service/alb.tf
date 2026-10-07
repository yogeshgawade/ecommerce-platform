resource "aws_lb" "auth" {
  name               = "${var.project_name}-${var.environment}-auth-alb"
  internal           = false
  load_balancer_type = "application"

  subnets         = aws_subnet.public[*].id
  security_groups = [aws_security_group.alb.id]

  enable_deletion_protection = false

  tags = {
    Name = "${var.project_name}-${var.environment}-auth-alb"
  }
}

resource "aws_lb_target_group" "auth" {
  name        = "${var.project_name}-${var.environment}-auth-tg"
  port        = var.auth_container_port
  protocol    = "HTTP"
  target_type = "ip"
  vpc_id      = aws_vpc.main.id

  health_check {
    enabled             = true
    protocol            = "HTTP"
    path                = "/actuator/health"
    port                = "traffic-port"
    healthy_threshold   = 2
    unhealthy_threshold = 3
    timeout             = 5
    interval            = 15
    matcher             = "200"
  }

  tags = {
    Name = "${var.project_name}-${var.environment}-auth-tg"
  }
}

resource "aws_lb_listener" "auth_http" {
  load_balancer_arn = aws_lb.auth.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.auth.arn
  }

  tags = {
    Name = "${var.project_name}-${var.environment}-auth-http-listener"
  }
}

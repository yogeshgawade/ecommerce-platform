resource "aws_cloudwatch_log_group" "auth" {
  name              = "/ecs/${var.project_name}-${var.environment}-auth-service"
  retention_in_days = 1

  tags = {
    Name = "${var.project_name}-${var.environment}-auth-service-logs"
  }
}

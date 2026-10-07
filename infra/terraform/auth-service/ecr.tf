resource "aws_ecr_repository" "auth" {
  name                 = "${var.project_name}-${var.environment}-auth-service"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  force_delete = true

  tags = {
    Name = "${var.project_name}-${var.environment}-auth-service-ecr"
  }
}

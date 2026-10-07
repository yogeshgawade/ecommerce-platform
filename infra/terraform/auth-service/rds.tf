resource "aws_db_subnet_group" "auth" {
  name = "${var.project_name}-${var.environment}-auth-db-subnet-group"

  subnet_ids = aws_subnet.private[*].id

  tags = {
    Name = "${var.project_name}-${var.environment}-auth-db-subnet-group"
  }
}

resource "aws_db_instance" "auth" {
  identifier = "${var.project_name}-${var.environment}-auth-db"

  engine         = "postgres"
  engine_version = "16"

  instance_class        = var.db_instance_class
  allocated_storage     = 20
  max_allocated_storage = 20
  storage_type          = "gp2"

  db_name  = var.db_name
  username = var.db_username
  password = var.db_password
  port     = 5432

  db_subnet_group_name   = aws_db_subnet_group.auth.name
  vpc_security_group_ids = [aws_security_group.rds.id]

  publicly_accessible = false

  multi_az = false

  backup_retention_period = 1

  skip_final_snapshot = true
  deletion_protection = false

  apply_immediately = true

  tags = {
    Name = "${var.project_name}-${var.environment}-auth-db"
  }
}

output "alb_dns_name" {
  description = "DNS name of the Auth Service Application Load Balancer"
  value       = aws_lb.auth.dns_name
}

output "auth_service_url" {
  description = "Base URL for the Auth Service"
  value       = "http://${aws_lb.auth.dns_name}"
}

output "ecr_repository_url" {
  description = "ECR repository URL for the Auth Service"
  value       = aws_ecr_repository.auth.repository_url
}

output "ecs_cluster_name" {
  description = "ECS cluster name"
  value       = aws_ecs_cluster.auth.name
}

output "ecs_service_name" {
  description = "ECS Auth Service name"
  value       = aws_ecs_service.auth.name
}

output "rds_endpoint" {
  description = "RDS PostgreSQL endpoint"
  value       = aws_db_instance.auth.address
}

output "rds_port" {
  description = "RDS PostgreSQL port"
  value       = aws_db_instance.auth.port
}

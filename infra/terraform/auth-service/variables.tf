variable "aws_region" {
  description = "AWS region for the Auth Service infrastructure"
  type        = string
  default     = "ap-south-1"
}

variable "environment" {
  description = "Deployment environment"
  type        = string
  default     = "test"
}

variable "project_name" {
  description = "Project name used for resource naming"
  type        = string
  default     = "ecommerce"
}

variable "vpc_cidr" {
  description = "CIDR block for the Auth Service VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "availability_zones" {
  description = "Availability Zones used by the infrastructure"
  type        = list(string)
  default = [
    "ap-south-1a",
    "ap-south-1b"
  ]
}

variable "db_name" {
  description = "PostgreSQL database name"
  type        = string
  default     = "auth_db"
}

variable "db_username" {
  description = "PostgreSQL master username"
  type        = string
  default     = "ecommerce"
}

variable "db_password" {
  description = "PostgreSQL master password"
  type        = string
  sensitive   = true
}

variable "jwt_secret" {
  description = "JWT signing secret used by the Auth Service"
  type        = string
  sensitive   = true
}

variable "db_instance_class" {
  description = "RDS PostgreSQL instance class"
  type        = string
  default     = "db.t3.micro"
}

variable "ecs_cpu" {
  description = "CPU units allocated to the Auth Service ECS task"
  type        = number
  default     = 256
}

variable "ecs_memory" {
  description = "Memory in MB allocated to the Auth Service ECS task"
  type        = number
  default     = 512
}

variable "auth_container_port" {
  description = "Port exposed by the Auth Service container"
  type        = number
  default     = 8080
}

variable "desired_count" {
  description = "Number of Auth Service ECS tasks"
  type        = number
  default     = 1
}

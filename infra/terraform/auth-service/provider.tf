provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "ecommerce-platform"
      Environment = var.environment
      Service     = "auth-service"
      ManagedBy   = "terraform"
    }
  }
}

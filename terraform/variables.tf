variable "aws_region" {
  description = "AWS region to deploy resources"
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Deployment environment name"
  type        = string
  default     = "production"
}

variable "app_name" {
  description = "Name of the application"
  type        = string
  default     = "health-app-backend"
}

variable "container_port" {
  description = "Port exposed by the backend container"
  type        = number
  default     = 8080
}

variable "db_name" {
  description = "Name of the PostgreSQL database"
  type        = string
  default     = "healthapp"
}

variable "db_username" {
  description = "Database administrator username"
  type        = string
  default     = "dbadmin"
}

variable "db_password" {
  description = "Database administrator password (override in production!)"
  type        = string
  sensitive   = true
  default     = "SuperSecurePassword123!"
}

variable "ecs_cpu" {
  description = "CPU units for ECS Fargate task (1024 = 1 vCPU)"
  type        = number
  default     = 256
}

variable "ecs_memory" {
  description = "Memory (in MB) for ECS Fargate task"
  type        = number
  default     = 512
}

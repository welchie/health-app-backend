variable "aws_region" {
  description = "AWS region to deploy resources"
  type        = string
  default     = "eu-west-1"
}

variable "environment" {
  description = "Deployment environment name"
  type        = string
  default     = "production"
}

variable "app_name" {
  description = "Name of the application"
  type        = string
  default     = "health-app"
}

variable "instance_type" {
  description = "EC2 instance type (t4g.micro Graviton2 ARM64)"
  type        = string
  default     = "t4g.micro"
}

variable "instance_architecture" {
  description = "Architecture for the EC2 instance (arm64 for Graviton, x86_64 for Intel/AMD)"
  type        = string
  default     = "arm64"
}

variable "db_name" {
  description = "Name of the PostgreSQL database"
  type        = string
  default     = "healthapp"
}

variable "db_username" {
  description = "Database administrator username"
  type        = string
  default     = "postgres"
}

variable "db_password" {
  description = "Database administrator password"
  type        = string
  sensitive   = true
  default     = "mysecurepassword"
}

variable "domain_name" {
  description = "Optional: Domain name pointing to this server (e.g. api.yourdomain.com) for auto-SSL"
  type        = string
  default     = "api.weewelchie.org"
}

variable "ssh_key_name" {
  description = "Optional: The name of an existing EC2 Key Pair in your AWS account to enable SSH access"
  type        = string
  default     = ""
}

variable "alert_email" {
  description = "Email address to receive health check and monitoring alerts"
  type        = string
  default     = "welchie99@gmail.com"
}


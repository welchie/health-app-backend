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
  description = "EC2 instance type (t3.micro is Free Tier eligible or extremely low cost)"
  type        = string
  default     = "t3.micro"
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
  default     = ""
}

variable "ssh_key_name" {
  description = "Optional: The name of an existing EC2 Key Pair in your AWS account to enable SSH access"
  type        = string
  default     = ""
}

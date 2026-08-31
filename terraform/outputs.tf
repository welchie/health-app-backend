output "ecr_repository_url" {
  description = "The URL of the ECR container repository"
  value       = aws_ecr_repository.backend.repository_url
}

output "alb_dns_name" {
  description = "The public URL (DNS name) of the load balancer to access the API"
  value       = "http://${aws_lb.main.dns_name}"
}

output "rds_endpoint" {
  description = "The connection endpoint for the RDS database"
  value       = aws_db_instance.postgres.endpoint
}

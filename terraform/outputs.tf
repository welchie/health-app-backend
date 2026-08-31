output "server_public_ip" {
  description = "The static public IP (Elastic IP) of the EC2 server. Point your domain (A record) to this IP."
  value       = aws_eip.ip.public_ip
}

output "ssm_connect_command" {
  description = "AWS CLI command to connect to your instance securely without SSH keys"
  value       = "aws ssm start-session --target ${aws_instance.server.id} --region ${var.aws_region}"
}

output "ssh_connect_command" {
  description = "Standard SSH command to connect (requires adding an SSH key to the instance)"
  value       = "ssh ec2-user@${aws_eip.ip.public_ip}"
}

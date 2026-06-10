output "ecr_repository_url" {
  description = "ECR repository URL used to tag and push the Docker image."
  value       = aws_ecr_repository.app.repository_url
}

output "ec2_public_ip" {
  description = "Public IP address of the EC2 instance."
  value       = aws_instance.app.public_ip
}

output "application_url" {
  description = "Public URL of the Spring Boot application."
  value       = "http://${aws_instance.app.public_ip}:${var.application_port}"
}


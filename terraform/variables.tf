variable "aws_region" {
  description = "AWS region for all resources."
  type        = string
  default     = "us-east-1"
}

variable "project_name" {
  description = "Name used for the EC2 instance and related resources."
  type        = string
  default     = "springboot-demo"
}

variable "ecr_repository_name" {
  description = "Name of the private ECR repository."
  type        = string
  default     = "springboot-demo"
}

variable "image_tag" {
  description = "Docker image tag that EC2 pulls."
  type        = string
  default     = "latest"
}

variable "deployment_id" {
  description = "Unique deployment identifier used to replace EC2 on each deployment."
  type        = string
  default     = "manual"
}

variable "instance_type" {
  description = "EC2 instance type."
  type        = string
  default     = "t3.micro"
}

variable "ec2_instance_profile_name" {
  description = "Name of an existing sandbox IAM instance profile that EC2 is allowed to use."
  type        = string

  validation {
    condition     = length(trimspace(var.ec2_instance_profile_name)) > 0
    error_message = "ec2_instance_profile_name must not be empty."
  }
}

variable "application_port" {
  description = "Spring Boot application port."
  type        = number
  default     = 8080
}

variable "allowed_cidr" {
  description = "CIDR allowed to call port 8080. Restrict this to your IP when possible."
  type        = string
  default     = "0.0.0.0/0"
}

variable "ssh_allowed_cidr" {
  description = "CIDR allowed to connect over SSH."
  type        = string
  default     = "0.0.0.0/0"
}

variable "ssh_public_key" {
  description = "Optional OpenSSH public key. Leave empty when SSH login is not needed."
  type        = string
  default     = ""
  sensitive   = true
}

variable "s3_bucket_name" {
  description = "Existing private S3 bucket used by the application."
  type        = string

  validation {
    condition     = length(trimspace(var.s3_bucket_name)) > 0
    error_message = "s3_bucket_name must not be empty."
  }
}

variable "region" {
  description = "AWS region. Learner Lab only allows us-east-1 and us-west-2."
  type        = string
  default     = "us-east-1"
}

variable "enable_cloudfront" {
  description = "Put a CloudFront distribution in front of the ALB to get HTTPS on a *.cloudfront.net URL without owning a domain."
  type        = bool
  default     = true
}

variable "enable_rds" {
  description = "Create an RDS MySQL database so links survive blue/green deploys."
  type        = bool
  default     = true
}

variable "db_password" {
  description = "Master password of the RDS database (also store it in the MYSQL_PWD GitHub secret). 8+ chars, no / @ \" or spaces."
  type        = string
  sensitive   = true
  default     = ""
}

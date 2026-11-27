terraform {
  required_version = ">= 1.5"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

# Credentials come from the environment (AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY,
# AWS_SESSION_TOKEN) or ~/.aws/credentials -- the AWS Academy Learner Lab "AWS Details" panel gives you both.
provider "aws" {
  region = var.region
}

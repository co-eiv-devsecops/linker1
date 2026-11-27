output "alb_dns_name" {
  value = aws_lb.linker1.dns_name
}

output "alb_url" {
  value = "http://${aws_lb.linker1.dns_name}"
}

output "https_url" {
  description = "Public HTTPS URL (empty if CloudFront is disabled)"
  value       = var.enable_cloudfront ? "https://${aws_cloudfront_distribution.linker1[0].domain_name}" : ""
}

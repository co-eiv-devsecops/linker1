# One-time "base" stack: load balancer, two target groups (A/B = blue/green),
# security groups and (optionally) CloudFront for HTTPS. The VMs themselves are
# created per deploy by scripts/aws/bluegreen.sh, not by Terraform, so this
# stack never has to be re-applied between deploys.
#
# Only uses default-VPC resources and no IAM, so it works inside AWS Academy
# Learner Lab (which forbids creating IAM roles).

data "aws_vpc" "default" {
  default = true
}

data "aws_subnets" "default" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }
}

resource "aws_security_group" "alb" {
  name        = "linker1-alb"
  description = "linker1 load balancer"
  vpc_id      = data.aws_vpc.default.id
}

resource "aws_vpc_security_group_ingress_rule" "alb_http" {
  security_group_id = aws_security_group.alb.id
  ip_protocol       = "tcp"
  from_port         = 80
  to_port           = 80
  cidr_ipv4         = "0.0.0.0/0"
}

resource "aws_vpc_security_group_egress_rule" "alb_all" {
  security_group_id = aws_security_group.alb.id
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
}

# Port 8081 (test listener) is intentionally closed here: the deploy script
# opens it for the CI runner's IP only while the functional tests run.

resource "aws_security_group" "instance" {
  name        = "linker1-instance"
  description = "linker1 VMs: app port reachable only from the load balancer"
  vpc_id      = data.aws_vpc.default.id
}

resource "aws_vpc_security_group_ingress_rule" "instance_from_alb" {
  security_group_id            = aws_security_group.instance.id
  ip_protocol                  = "tcp"
  from_port                    = 8080
  to_port                      = 8080
  referenced_security_group_id = aws_security_group.alb.id
}

resource "aws_vpc_security_group_egress_rule" "instance_all" {
  security_group_id = aws_security_group.instance.id
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
}

resource "aws_lb" "linker1" {
  name               = "linker1-alb"
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = data.aws_subnets.default.ids
}

# Two target groups: whichever one currently receives the weight is "blue";
# the deploy script registers the new VM in the other one ("green").
resource "aws_lb_target_group" "slot" {
  for_each = toset(["a", "b"])

  name                 = "linker1-${each.key}"
  port                 = 8080
  protocol             = "HTTP"
  vpc_id               = data.aws_vpc.default.id
  deregistration_delay = 30

  # "/" like the original OCI setup: keeps LB health independent of MySQL.
  health_check {
    path                = "/"
    matcher             = "200"
    interval            = 15
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }
}

resource "aws_lb_listener" "main" {
  load_balancer_arn = aws_lb.linker1.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type = "forward"
    forward {
      target_group {
        arn    = aws_lb_target_group.slot["a"].arn
        weight = 100
      }
      target_group {
        arn    = aws_lb_target_group.slot["b"].arn
        weight = 0
      }
    }
  }

  # The deploy script changes the weights; do not let `terraform apply` undo them.
  lifecycle {
    ignore_changes = [default_action]
  }
}

# Test listener: always points at the idle (green) group during a deploy.
resource "aws_lb_listener" "test" {
  load_balancer_arn = aws_lb.linker1.arn
  port              = 8081
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.slot["b"].arn
  }

  lifecycle {
    ignore_changes = [default_action]
  }
}

# AWS-managed policy IDs, hardcoded on purpose: Learner Lab denies
# cloudfront:ListCachePolicies / ListOriginRequestPolicies, so they cannot be
# looked up by name. These IDs are fixed and documented by AWS.
locals {
  cf_cache_policy_caching_disabled    = "4135ea2d-6df8-44a3-9df3-4b5a84be39ad" # Managed-CachingDisabled
  cf_origin_request_policy_all_viewer = "216adef6-5c7f-47e4-b989-5492eafa07d3" # Managed-AllViewer
}

# HTTPS without a domain: CloudFront's default *.cloudfront.net certificate.
# Caching is disabled on purpose: GET /{id} must reach the app every time so
# the click counter (HU-04) keeps working and redirects are never cached.
resource "aws_cloudfront_distribution" "linker1" {
  count   = var.enable_cloudfront ? 1 : 0
  enabled = true
  comment = "linker1"

  origin {
    origin_id   = "alb"
    domain_name = aws_lb.linker1.dns_name

    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "http-only"
      origin_ssl_protocols   = ["TLSv1.2"]
    }
  }

  default_cache_behavior {
    target_origin_id         = "alb"
    viewer_protocol_policy   = "redirect-to-https"
    allowed_methods          = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
    cached_methods           = ["GET", "HEAD"]
    cache_policy_id          = local.cf_cache_policy_caching_disabled
    origin_request_policy_id = local.cf_origin_request_policy_all_viewer
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    cloudfront_default_certificate = true
  }
}

# ── Optional RDS MySQL: persistent link storage across blue/green deploys ──
resource "aws_security_group" "db" {
  count       = var.enable_rds ? 1 : 0
  name        = "linker1-db"
  description = "linker1 MySQL: reachable only from the app VMs"
  vpc_id      = data.aws_vpc.default.id
}

resource "aws_vpc_security_group_ingress_rule" "db_from_instances" {
  count                        = var.enable_rds ? 1 : 0
  security_group_id            = aws_security_group.db[0].id
  ip_protocol                  = "tcp"
  from_port                    = 3306
  to_port                      = 3306
  referenced_security_group_id = aws_security_group.instance.id
}

resource "aws_db_subnet_group" "linker1" {
  count      = var.enable_rds ? 1 : 0
  name       = "linker1-db"
  subnet_ids = data.aws_subnets.default.ids
}

resource "aws_db_instance" "linker1" {
  count                   = var.enable_rds ? 1 : 0
  identifier              = "linker1-db"
  engine                  = "mysql"
  engine_version          = "8.0"
  instance_class          = "db.t3.micro"
  allocated_storage       = 20
  db_name                 = "linker"
  username                = "linker"
  password                = var.db_password
  db_subnet_group_name    = aws_db_subnet_group.linker1[0].name
  vpc_security_group_ids  = [aws_security_group.db[0].id]
  publicly_accessible     = false
  multi_az                = false
  backup_retention_period = 0
  skip_final_snapshot     = true
  apply_immediately       = true

  lifecycle {
    precondition {
      condition     = length(var.db_password) >= 8
      error_message = "Set db_password (8+ characters) when enable_rds is true."
    }
  }
}

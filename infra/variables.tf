variable "region" {
  type    = string
  default = "sa-bogota-1"
}

variable "compartment_id" {
  type = string
}

variable "subnet_id" {
  type = string
}

variable "image_id" {
  type = string
}

variable "ssh_public_key" {
  type = string
}

variable "repo_url" {
  type    = string
  default = "https://github.com/co-eiv-devsecops/linker1"
}

variable "ld_sdk_key" {
  description = "LaunchDarkly SDK key injected into the linker1 systemd service unit"
  type        = string
  sensitive   = true
}

variable "otel_exporter_otlp_endpoint" {
  description = "OTLP endpoint (e.g. Grafana Cloud) for exporting logs/metrics/traces. Left empty, the app runs with OTLP export disabled instead of failing to start."
  type        = string
  default     = ""
}

variable "otel_exporter_otlp_headers" {
  description = "OTLP exporter auth headers (e.g. \"Authorization=Basic <token>\" for Grafana Cloud). Left empty, no auth headers are sent."
  type        = string
  sensitive   = true
  default     = ""
}

variable "mysql_host" {
  description = "MySQL host used by GET /healthz's SELECT 1 check. Left empty, the healthcheck reports unhealthy instead of the app failing to start."
  type        = string
  default     = ""
}

variable "mysql_database" {
  description = "MySQL database name for the healthcheck connection."
  type        = string
  default     = ""
}

variable "mysql_user" {
  description = "MySQL user for the healthcheck connection."
  type        = string
  default     = ""
}

variable "mysql_pwd" {
  description = "MySQL password for the healthcheck connection."
  type        = string
  sensitive   = true
  default     = ""
}

variable "instance_shape" {
  type    = string
  default = "VM.Standard.E5.Flex"
}

variable "instance_ocpus" {
  type    = number
  default = 1
}

variable "instance_memory_gb" {
  type    = number
  default = 4
}

variable "instance_display_name" {
  type    = string
  default = "vm-linker1-iac"
}

variable "availability_domain" {
  type    = string
  default = "AD-1"
}
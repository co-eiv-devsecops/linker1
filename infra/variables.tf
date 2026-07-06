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
  description = "LaunchDarkly SDK key inyectada en el systemd unit del servicio linker1"
  type        = string
  sensitive   = true
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
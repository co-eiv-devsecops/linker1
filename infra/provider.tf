terraform {
  required_providers {
    oci = {
      source  = "oracle/oci"
      version = ">= 5.0.0"
    }
  }
}

# OCI provider authentication.
# In CI these are supplied via TF_VAR_* environment variables so that no
# ~/.oci/config file is needed on the runner.
# For local use, leave them empty (default "") and the provider falls back
# to ~/.oci/config as usual.
variable "tenancy_ocid" {
  description = "OCI tenancy OCID. In CI: secrets.OCI_CLI_TENANCY → TF_VAR_tenancy_ocid."
  type        = string
  default     = ""
  sensitive   = true
}

variable "user_ocid" {
  description = "OCI user OCID. In CI: secrets.OCI_CLI_USER → TF_VAR_user_ocid."
  type        = string
  default     = ""
  sensitive   = true
}

variable "fingerprint" {
  description = "OCI API key fingerprint. In CI: secrets.OCI_CLI_FINGERPRINT → TF_VAR_fingerprint."
  type        = string
  default     = ""
}

variable "private_key" {
  description = "OCI API private key content. In CI: secrets.OCI_CLI_KEY_CONTENT → TF_VAR_private_key."
  type        = string
  default     = ""
  sensitive   = true
}

provider "oci" {
  region       = var.region
  tenancy_ocid = var.tenancy_ocid != "" ? var.tenancy_ocid : null
  user_ocid    = var.user_ocid    != "" ? var.user_ocid    : null
  fingerprint  = var.fingerprint  != "" ? var.fingerprint  : null
  private_key  = var.private_key  != "" ? var.private_key  : null
}
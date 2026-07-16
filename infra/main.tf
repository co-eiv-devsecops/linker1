data "oci_core_subnet" "linker1_subnet" {
  subnet_id = var.subnet_id
}

# Discover the first availability domain automatically so the workflow never
# needs to know the full AD name (e.g. "gnbE:SA-BOGOTA-1-AD-1").
data "oci_identity_availability_domains" "ads" {
  compartment_id = var.compartment_id
}

locals {
  # systemd's unit-file parser treats a bare "%" as the start of a specifier
  # (%h, %n, ...) and a bare '"' inside a quoted Environment= value as the end
  # of that value -- both are real characters a Grafana Cloud token or a
  # database password can legitimately contain. Escaping them here (before
  # they ever reach cloud-init.yaml's Environment="..." lines) is the same
  # fix applied to deploy.sh and the CI/CD pipeline's equivalent step, after
  # an unescaped "%" in a secret crashed both of those.
  systemd_escape = { for k, v in {
    ld_sdk_key                  = var.ld_sdk_key
    otel_exporter_otlp_endpoint = var.otel_exporter_otlp_endpoint
    otel_exporter_otlp_headers  = var.otel_exporter_otlp_headers
    mysql_host                  = var.mysql_host
    mysql_database              = var.mysql_database
    mysql_user                  = var.mysql_user
    mysql_pwd                   = var.mysql_pwd
    } : k => replace(replace(v, "%", "%%"), "\"", "\\\"")
  }

  cloud_init_rendered = templatefile("${path.module}/cloud-init.yaml", {
    repo_url                    = var.repo_url
    ld_sdk_key                  = local.systemd_escape.ld_sdk_key
    otel_exporter_otlp_endpoint = local.systemd_escape.otel_exporter_otlp_endpoint
    otel_exporter_otlp_headers  = local.systemd_escape.otel_exporter_otlp_headers
    mysql_host                  = local.systemd_escape.mysql_host
    mysql_database              = local.systemd_escape.mysql_database
    mysql_user                  = local.systemd_escape.mysql_user
    mysql_pwd                   = local.systemd_escape.mysql_pwd
  })
}

resource "oci_core_instance" "linker1_vm" {
  compartment_id      = var.compartment_id
  availability_domain = data.oci_identity_availability_domains.ads.availability_domains[0].name
  display_name        = var.instance_display_name
  shape               = var.instance_shape

  shape_config {
    ocpus         = var.instance_ocpus
    memory_in_gbs = var.instance_memory_gb
  }

  create_vnic_details {
    subnet_id        = data.oci_core_subnet.linker1_subnet.id
    assign_public_ip = false
  }

  source_details {
    source_type = "image"
    source_id   = var.image_id
  }

  metadata = {
    ssh_authorized_keys = var.ssh_public_key
    user_data           = base64encode(local.cloud_init_rendered)
  }

  # Off by default on new instances -- without it, `oci bastion session
  # create-managed-ssh` fails with "the Bastion plugin must be enabled on the
  # target instance" and there's no way to reach the VM to debug anything.
  # Discovered the hard way: the VM this fix followed had no way to be
  # inspected when its OCI Load Balancer backend health went CRITICAL.
  agent_config {
    plugins_config {
      name          = "Bastion"
      desired_state = "ENABLED"
    }
  }

  lifecycle {
    ignore_changes = [source_details[0].source_id]
  }
}
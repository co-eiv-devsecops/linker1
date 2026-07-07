data "oci_core_subnet" "linker1_subnet" {
  subnet_id = var.subnet_id
}

locals {
  cloud_init_rendered = templatefile("${path.module}/cloud-init.yaml", {
    repo_url                    = var.repo_url
    ld_sdk_key                  = var.ld_sdk_key
    otel_exporter_otlp_endpoint = var.otel_exporter_otlp_endpoint
  })
}

resource "oci_core_instance" "linker1_vm" {
  compartment_id      = var.compartment_id
  availability_domain = var.availability_domain
  display_name        = var.instance_display_name
  shape                = var.instance_shape

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

  lifecycle {
    ignore_changes = [source_details[0].source_id]
  }
}
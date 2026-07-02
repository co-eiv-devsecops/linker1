output "instance_id" {
  value = oci_core_instance.linker1_vm.id
}

output "instance_private_ip" {
  value = oci_core_instance.linker1_vm.private_ip
}

output "instance_state" {
  value = oci_core_instance.linker1_vm.state
}
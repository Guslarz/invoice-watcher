locals {
  manifest_names = [
    "app-namespace.yml",
    "pod-sa.yml",
    "image-pull-secret.yml"
  ]
  manifest_files = [for name in local.manifest_names : "${path.module}/resources/manifests/${name}"]
  parsed_manifest_files = [
    for manifest_file in local.manifest_files : templatefile(manifest_file, {
      gcp_project_number                  = var.gcp_project_number
      ghcr_docker_config_json_secret_name = var.ghcr_docker_config_json_secret_name
      namespace                           = var.k8s-namespace
      sa_name                             = var.k8s-sa-name
    })
  ]
  decoded_manifests = flatten([
    for manifest_file in local.parsed_manifest_files : provider::kubernetes::manifest_decode_multi(manifest_file)
  ])
  manifests = {
    for manifest in local.decoded_manifests :
    "${manifest.kind}-${manifest.metadata.name}" => manifest
  }
}

resource "kubernetes_manifest" "manifests" {
  for_each = local.manifests
  manifest = each.value
}

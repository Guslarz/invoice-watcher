output "host" {
  value = "https://${google_container_cluster.cluster.endpoint}"
}

output "access_token" {
  value = data.google_client_config.default.access_token
}

output "ca_certificate" {
  value = google_container_cluster.cluster.master_auth.0.cluster_ca_certificate
}

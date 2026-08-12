output "gke_gateway_ip" {
  value = google_compute_global_address.gateway_ip.address
}

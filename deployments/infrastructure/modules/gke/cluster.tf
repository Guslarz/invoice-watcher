resource "google_container_cluster" "cluster" {
  name                     = var.cluster_name
  location                 = var.gcp_zone
  initial_node_count       = 1
  deletion_protection      = false
  remove_default_node_pool = true

  workload_identity_config {
    workload_pool = "${var.gcp_project_id}.svc.id.goog"
  }

  secret_manager_config {
    enabled = true
  }

  secret_sync_config {
    enabled = true
  }

  gateway_api_config {
    channel = "CHANNEL_STANDARD"
  }

  depends_on = [
    google_project_service.gke_api,
  ]
}

resource "google_container_node_pool" "default" {
  name       = "default"
  location   = var.gcp_zone
  cluster    = google_container_cluster.cluster.name
  node_count = 1

  node_config {
    machine_type    = "e2-standard-2"
    preemptible     = true
    service_account = google_service_account.gke_sa.email
  }
}

resource "google_compute_global_address" "gateway_ip" {
  name = "gke-gateway-ip"
}

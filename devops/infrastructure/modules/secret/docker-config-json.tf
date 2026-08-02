resource "google_secret_manager_secret" "gke_docker_config_json" {
  secret_id  = "gke_docker_config_json"
  depends_on = [google_project_service.secret_manager_api]

  replication {
    user_managed {
      replicas {
        location = var.gcp_region
      }
    }
  }
}

resource "google_secret_manager_secret_version" "gke_docker_config_json" {
  secret      = google_secret_manager_secret.gke_docker_config_json.id
  secret_data = "{\"auths\":{\"${var.github_registry}\":{\"auth\":\"${base64encode("${var.github_username}:${var.github_pat}")}\"}}}"
}

resource "google_secret_manager_secret" "ghcr_docker_config_json" {
  secret_id  = "ghcr_docker_config_json"
  depends_on = [google_project_service.secret_manager_api]

  replication {
    auto {}
  }
}

resource "google_secret_manager_secret_version" "ghcr_docker_config_json" {
  secret      = google_secret_manager_secret.ghcr_docker_config_json.id
  secret_data = "{\"auths\":{\"${var.github_registry}\":{\"auth\":\"${base64encode("${var.github_username}:${var.github_pat}")}\"}}}"
}

resource "google_secret_manager_secret" "google_oauth" {
  secret_id  = "google_oauth"
  depends_on = [google_project_service.secret_manager_api]
  replication {
    auto {}
  }
}

resource "google_secret_manager_secret_version" "google_oauth" {
  secret = google_secret_manager_secret.google_oauth.id
  secret_data = jsonencode({
    client_id     = var.google_oauth_client_id
    client_secret = var.google_oauth_client_secret
  })
}

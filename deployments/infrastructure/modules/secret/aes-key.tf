resource "google_secret_manager_secret" "aes_key" {
  secret_id  = "aes_key"
  depends_on = [google_project_service.secret_manager_api]

  replication {
    auto {}
  }
}

resource "google_secret_manager_secret_version" "aes_key" {
  secret      = google_secret_manager_secret.aes_key.id
  secret_data = var.aes_key
}

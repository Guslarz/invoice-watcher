resource "google_secret_manager_secret" "cloudflare_api_token" {
  secret_id  = "cloudflare_api_token"
  depends_on = [google_project_service.secret_manager_api]

  replication {
    auto {}
  }
}

resource "google_secret_manager_secret_version" "cloudflare_api_token" {
  secret      = google_secret_manager_secret.cloudflare_api_token.id
  secret_data = var.cloudflare_api_token
}

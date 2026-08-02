resource "google_secret_manager_secret" "github_pat" {
  secret_id  = "github_pat"
  depends_on = [google_project_service.secret_manager_api]

  replication {
    user_managed {
      replicas {
        location = var.gcp_region
      }
    }
  }
}

resource "google_secret_manager_secret_version" "github_pat" {
  secret      = google_secret_manager_secret.github_pat.id
  secret_data = var.github_pat
}

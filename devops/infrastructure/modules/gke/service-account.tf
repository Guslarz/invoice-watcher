resource "google_service_account" "gke_sa" {
  account_id = "gke-sa"
}

resource "google_project_iam_member" "gke_sa_datastore_user" {
  project = var.gcp_project_id
  role    = "roles/datastore.user"
  member  = "serviceAccount:${google_service_account.gke_sa.email}"
}

resource "google_project_iam_member" "gke_sa_secret_manager_secret_accessor" {
  project = var.gcp_project_id
  role    = "roles/secretmanager.secretAccessor"
  member  = "serviceAccount:${google_service_account.gke_sa.email}"
}

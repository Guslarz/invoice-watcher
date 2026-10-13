resource "google_project_iam_member" "gke_default_secret_sync_sa_secret_manager_secret_accessor" {
  project = var.gcp_project_id
  role    = "roles/secretmanager.secretAccessor"
  member  = "principal://iam.googleapis.com/projects/${var.gcp_project_number}/locations/global/workloadIdentityPools/${var.gcp_project_id}.svc.id.goog/subject/ns/cluster-init/sa/secret-sync-sa"

  depends_on = [
    google_container_cluster.cluster,
  ]
}

resource "google_project_iam_member" "app_sa_datastore_user" {
  project = var.gcp_project_id
  role    = "roles/datastore.user"
  member  = "principal://iam.googleapis.com/projects/${var.gcp_project_number}/locations/global/workloadIdentityPools/${var.gcp_project_id}.svc.id.goog/subject/ns/${var.app_namespace}/sa/app-sa"

  depends_on = [
    google_container_cluster.cluster,
  ]
}

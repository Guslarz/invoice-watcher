resource "google_project_iam_member" "gke_sa_datastore_user" {
  project = var.gcp_project_id
  role    = "roles/datastore.user"
  member  = "principal://iam.googleapis.com/projects/${data.google_project.current.number}/locations/global/workloadIdentityPools/${var.gcp_project_id}.svc.id.goog/subject/ns/${var.k8s-namespace}/sa/${var.k8s-sa-name}"
}

resource "google_project_iam_member" "gke_sa_secret_manager_secret_accessor" {
  project = var.gcp_project_id
  role    = "roles/secretmanager.secretAccessor"
  member  = "principal://iam.googleapis.com/projects/${data.google_project.current.number}/locations/global/workloadIdentityPools/${var.gcp_project_id}.svc.id.goog/subject/ns/${var.k8s-namespace}/sa/${var.k8s-sa-name}"
}

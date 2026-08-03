resource "google_project_service" "firestore_api" {
  service = "cloudresourcemanager.googleapis.com"
  disable_on_destroy = false
}

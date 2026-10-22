resource "google_project_service" "firestore_api" {
  service            = "firestore.googleapis.com"
  disable_on_destroy = false
}

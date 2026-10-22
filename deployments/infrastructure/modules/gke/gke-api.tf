resource "google_project_service" "gke_api" {
  service            = "container.googleapis.com"
  disable_on_destroy = false
}

resource "google_project_service" "cloud_resource_manager_api" {
  service = "cloudresourcemanager.googleapis.com"
  disable_on_destroy = false
}

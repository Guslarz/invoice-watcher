resource "google_firestore_database" "db" {
  name                        = "(default)"
  location_id                 = var.gcp_region
  type                        = "DATASTORE_MODE"
  app_engine_integration_mode = "DISABLED"
  depends_on = [
    google_project_service.firestore_api
  ]
  # Intended: the database goes away with the ephemeral environment.
  deletion_policy = "DELETE"
}

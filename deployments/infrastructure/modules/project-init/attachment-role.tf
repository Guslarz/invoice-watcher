resource "google_project_iam_custom_role" "capture" {
  project     = var.gcp_project_id
  role_id     = "attachmentCapture"
  title       = "Capture and reconcile attachment evidence"
  permissions = ["storage.objects.create", "storage.objects.get", "storage.buckets.get"]
}

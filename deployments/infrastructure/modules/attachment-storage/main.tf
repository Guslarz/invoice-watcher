terraform {
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 7.43"
    }
  }
}

resource "google_storage_bucket" "attachments" {
  name                        = "${var.gcp_project_id}-dev-mail-attachments"
  location                    = upper(var.gcp_region)
  uniform_bucket_level_access = true
  public_access_prevention    = "enforced"
  force_destroy               = true

  versioning {
    enabled = false
  }
  soft_delete_policy {
    retention_duration_seconds = 0
  }
  lifecycle_rule {
    condition {
      age = var.evidence_lifetime_days
    }
    action {
      type = "Delete"
    }
  }
}

resource "google_storage_bucket_iam_member" "capture" {
  bucket = google_storage_bucket.attachments.name
  role   = "projects/${var.gcp_project_id}/roles/attachmentCapture"
  member = var.capture_principal
}

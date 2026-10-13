provider "google" {
  project = var.gcp_project_id
  region  = var.gcp_region
}

module "project_init" {
  source              = "../../../modules/project-init"
  gcp_project_id      = var.gcp_project_id
  gcs_location        = upper(var.gcp_region)
  tfstate_bucket_name = "${var.gcp_project_id}-tfstate"
  github_repository   = var.github_repository
}

locals {
  tfstate_bucket_name = "${var.gcp_project_id}-tfstate"
}

terraform {
  backend "gcs" {
    bucket = local.tfstate_bucket_name
    prefix = "infrastructure/dev-persistent"
  }
}

provider "google" {
  project = var.gcp_project_id
  region  = var.gcp_region
}

module "project_init" {
  source              = "../../modules/project-init"
  gcs_location        = upper(var.gcp_region)
  tfstate_bucket_name = local.tfstate_bucket_name
  github_repository   = var.github_repository
}

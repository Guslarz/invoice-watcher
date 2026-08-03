terraform {
  backend "gcs" {
    bucket = "invoice-watcher-terraform-state"
    prefix = "dev"
  }
}

provider "google" {
  project = var.gcp_project_id
  region = var.gcp_region
  version = "7.42.0"
}

module "secret" {
  source          = "../../modules/secret"
  providers = {
    google = google
  }
  gcp_project_id  = var.gcp_project_id
  gcp_region      = var.gcp_region
  github_registry = var.github_registry
  github_username = var.github_username
  github_pat      = var.github_pat
}

module "firestore" {
  source         = "../../modules/firestore"
  providers = {
    google = google
  }
  gcp_project_id = var.gcp_project_id
  gcp_region     = var.gcp_region
}

module "gke" {
  source         = "../../modules/gke"
  providers = {
    google = google
  }
  gcp_project_id = var.gcp_project_id
  gcp_region     = var.gcp_region
  gcp_zone       = var.gcp_zone
  cluster_name   = var.gcp_gke_cluster_name
  depends_on = [
    module.secret,
    module.firestore
  ]
}

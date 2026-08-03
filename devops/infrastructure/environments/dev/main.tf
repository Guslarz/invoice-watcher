terraform {
  backend "gcs" {
    bucket = "invoice-watcher-terraform-state"
    prefix = "dev"
  }

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "7.42.0"
    }
  }
}

provider "google" {
  project = var.gcp_project_id
  region  = var.gcp_region
}

module "core" {
  source = "../../modules/core"
}

module "secret" {
  source          = "../../modules/secret"
  gcp_region      = var.gcp_region
  github_registry = var.github_registry
  github_username = var.github_username
  github_pat      = var.github_pat
  depends_on = [
    module.core
  ]
}

module "firestore" {
  source     = "../../modules/firestore"
  gcp_region = var.gcp_region
  depends_on = [
    module.core
  ]
}

module "gke" {
  source         = "../../modules/gke"
  gcp_project_id = var.gcp_project_id
  gcp_zone       = var.gcp_zone
  cluster_name   = var.gcp_gke_cluster_name
  depends_on = [
    module.core,
    module.secret,
    module.firestore
  ]
}

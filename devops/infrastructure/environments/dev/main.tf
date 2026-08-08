locals {
  tfstate_bucket_name = "${var.gcp_project_id}-tfstate"
}

terraform {
  backend "gcs" {
    bucket = local.tfstate_bucket_name
    prefix = "dev"
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

module "secret" {
  source          = "../../modules/secret"
  gcp_region      = var.gcp_region
  github_registry = var.github_registry
  github_username = var.github_username
  github_pat      = var.github_pat
}

module "firestore" {
  source     = "../../modules/firestore"
  gcp_region = var.gcp_region
}

module "gke" {
  source             = "../../modules/gke"
  gcp_zone           = var.gcp_zone
  cluster_name       = var.gcp_gke_cluster_name
  k8s-namespace      = var.k8s-namespace
  k8s-sa-name        = var.k8s-sa-name

  depends_on = [
    module.secret,
    module.firestore
  ]
}

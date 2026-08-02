terraform {
  backend "gcs" {
    bucket = "invoice-watcher-terraform-state"
    prefix = "dev"
  }
}

module "firestore" {
  source         = "../../modules/firestore"
  gcp_project_id = var.gcp_project_id
  gcp_region     = var.gcp_region
}

module "gke" {
  source         = "../../modules/gke"
  gcp_project_id = var.gcp_project_id
  gcp_region     = var.gcp_region
  gcp_zone       = var.gcp_zone
  cluster_name   = var.gcp_gke_cluster_name
}

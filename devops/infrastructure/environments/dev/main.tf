terraform {
  backend "gcs" {
    bucket = "invoice-watcher-terraform-state"
    prefix = "dev"
  }
  required_providers {
    helm = {
      source  = "hashicorp/helm"
      version = "3.1.1"
    }
  }
}

provider "google" {
  project = var.gcp_project_id
  region  = var.gcp_region
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
  gcp_project_id     = var.gcp_project_id
  gcp_project_number = var.gcp_project_number
  gcp_zone           = var.gcp_zone
  cluster_name       = var.gcp_gke_cluster_name
  k8s-namespace      = var.k8s-namespace
  k8s-sa-name        = var.k8s-sa-name
  depends_on = [
    module.secret,
    module.firestore
  ]
}


data "google_client_config" "default" {}

data "google_container_cluster" "gke" {
  name       = var.gcp_gke_cluster_name
  location   = var.gcp_zone
  depends_on = [module.gke]
}

provider "kubernetes" {
  host                   = "https://${data.google_container_cluster.gke.endpoint}"
  cluster_ca_certificate = base64decode(data.google_container_cluster.gke.master_auth[0].cluster_ca_certificate)
  token                  = data.google_client_config.default.access_token
}

module "k8s_init" {
  source        = "../../modules/k8s-init"
  k8s-namespace = var.k8s-namespace
  k8s-sa-name   = var.k8s-sa-name
  gcp_project_number                  = var.gcp_project_number
  ghcr_docker_config_json_secret_name = var.ghcr_docker_config_json_secret_name
  depends_on = [
    module.gke
  ]
}

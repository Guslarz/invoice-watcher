locals {
  tfstate_bucket_name = "${var.gcp_project_id}-tfstate"
}

terraform {
  backend "gcs" {
    bucket = local.tfstate_bucket_name
    prefix = "infrastructure/dev/ephemeral"
  }

  required_version = ">= 1.12"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "7.43.0"
    }
    cloudflare = {
      source  = "cloudflare/cloudflare"
      version = "5.23.0"
    }
  }
}

provider "google" {
  project = var.gcp_project_id
  region  = var.gcp_region
}

provider "cloudflare" {
  api_token = var.cloudflare_api_token
}

module "secret" {
  source                     = "../../../modules/secret"
  github_pat                 = var.github_pat
  cloudflare_api_token       = var.cloudflare_api_token
  aes_key                    = var.aes_key
  google_oauth_client_id     = var.google_oauth_client_id
  google_oauth_client_secret = var.google_oauth_client_secret
}

module "firestore" {
  source     = "../../../modules/firestore"
  gcp_region = var.gcp_region
}

module "gke" {
  source             = "../../../modules/gke"
  gcp_project_id     = var.gcp_project_id
  gcp_project_number = var.gcp_project_number
  gcp_zone           = var.gcp_zone
  cluster_name       = var.gcp_gke_cluster_name
  app_namespace      = "invoice-watcher-dev"

  depends_on = [
    module.secret,
    module.firestore
  ]
}

module "cloudflare_domain" {
  source             = "../../../modules/cloudflare-domain"
  cloudflare_zone_id = var.cloudflare_zone_id
  domain_name        = var.domain_name
  ip_address         = module.gke.gke_gateway_ip
}

module "attachment_storage" {
  source                 = "../../../modules/attachment-storage"
  gcp_project_id         = var.gcp_project_id
  gcp_region             = var.gcp_region
  evidence_lifetime_days = jsondecode(file("${path.module}/../../../../installations/dev-mail-evidence.json")).evidenceLifetimeDays
  capture_principal      = "principal://iam.googleapis.com/projects/${var.gcp_project_number}/locations/global/workloadIdentityPools/${var.gcp_project_id}.svc.id.goog/subject/ns/invoice-watcher-dev/sa/mail-producer-sa"
  depends_on             = [module.gke]
}

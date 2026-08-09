variable "gcp_project_id" {
}

variable "gcp_region" {
  default = "europe-west1"
}

variable "gcp_zone" {
  default = "europe-west1-b"
}

variable "gcp_gke_cluster_name" {
  default = "invoice-watcher-cluster"
}

variable "github_registry" {
  default = "ghcr.io"
}

variable "github_repository" {
  default = "Guslarz/invoice-watcher"
}

variable "github_username" {
  default = "Guslarz"
}

variable "github_pat" {
  sensitive = true
}

variable "ghcr_docker_config_json_secret_name" {
  default = "ghcr_docker_config_json"
}

variable "k8s-namespace" {
  default = "invoice-watcher"
}

variable "k8s-sa-name" {
  default = "pod-sa"
}


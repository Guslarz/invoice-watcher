variable "gcp_project_id" {
}

variable "gcp_project_number" {
}

variable "gcp_region" {
}

variable "gcp_zone" {
}

variable "gcp_gke_cluster_name" {
}

variable "github_registry" {
  default = "ghcr.io"
}

variable "github_username" {
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


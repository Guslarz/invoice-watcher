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

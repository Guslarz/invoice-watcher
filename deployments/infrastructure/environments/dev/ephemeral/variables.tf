variable "gcp_project_id" {
}

variable "gcp_project_number" {
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

variable "github_pat" {
  sensitive = true
}

variable "cloudflare_api_token" {
  sensitive = true
}

variable "cloudflare_zone_id" {
}

variable "domain_name" {
  default = "guslarz.com"
}

variable "aes_key" {
  sensitive = true
}

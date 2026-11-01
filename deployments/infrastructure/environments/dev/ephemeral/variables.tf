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

variable "github_pat" {
  sensitive = true
}

variable "cloudflare_api_token" {
  sensitive = true
}

variable "cloudflare_zone_id" {
}

variable "domain_name" {
}

variable "aes_key" {
  sensitive = true
}

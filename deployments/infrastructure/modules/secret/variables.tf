variable "gcp_region" {
}

variable "github_registry" {
}

variable "github_username" {
}

variable "github_pat" {
  sensitive = true
}

variable "cloudflare_api_token" {
  sensitive = true
}

variable "aes_key" {
  sensitive = true
}

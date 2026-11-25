variable "gcp_project_id" {
  type = string
}
variable "gcp_region" {
  type = string
}
variable "capture_principal" {
  type = string
}
variable "evidence_lifetime_days" {
  type = number
  validation {
    condition     = var.evidence_lifetime_days == 9
    error_message = "The current bucket policy is fixed at nine days; retention changes require a new reviewed bucket policy."
  }
}

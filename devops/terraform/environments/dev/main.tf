module "firestore" {
  source         = "../../modules/firestore"
  gcp_project_id = var.gcp_project_id
  gcp_region     = var.gcp_region
}

# Kafka's data disk, bound by the static PersistentVolume kafka-data of the cluster-init chart.
# Created and destroyed with the environment; nothing in the cluster deletes it.
resource "google_compute_disk" "kafka_data" {
  name = "kafka-data"
  type = "pd-balanced"
  zone = var.gcp_zone
  # Keep in sync with the capacity in deployments/k8s/cluster-init/templates/storage/kafka-data-volume.yml
  # and the claim in deployments/k8s-apps/apps/kafka/base/statefulset.yml
  size = 10

  depends_on = [
    google_project_service.compute_api,
  ]
}

plugins {
  id("springboot.lib-conventions")
}

description = "Shared processing lifecycle, outbox and replay services."

dependencies {
  api(projects.libs.eventProcessorApi)
  api(projects.libs.daoEventProcessingApi)
  implementation(libs.spring.boot.starter.validation)
  implementation(libs.micrometer.core)
  testImplementation(projects.libs.daoEventProcessingFirestoreImpl)
  testImplementation(projects.libs.eventProcessorKafkaImpl)
  testImplementation(libs.google.cloud.datastore)
  testImplementation(libs.spring.kafka)
  testImplementation(libs.testcontainers.gcloud)
  testImplementation(libs.testcontainers.kafka)
  testImplementation(libs.testcontainers.junit.jupiter)
}

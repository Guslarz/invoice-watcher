plugins {
  id("springboot.lib-conventions")
}

description = "Durable processing state in Firestore in Datastore mode."

dependencies {
  api(projects.libs.daoEventProcessingApi)
  implementation(libs.spring.boot.starter.validation)
  implementation(libs.google.cloud.datastore)
  implementation(libs.jackson.databind)
  testImplementation(libs.testcontainers.gcloud)
  testImplementation(libs.testcontainers.junit.jupiter)
}

plugins {
  id("springboot.lib-conventions")
}

description = "Storage of users in Firestore in Datastore mode."

dependencies {
  api(projects.libs.daoUserConfigurationApi)

  implementation(libs.spring.boot.autoconfigure)
  implementation(libs.spring.boot.starter.validation)
  implementation(libs.spring.cloud.gcp.data.datastore)
  implementation(libs.spring.cloud.gcp.core)

  implementation(libs.commons.lang3)
  implementation(libs.jakarta.annotations)

  testImplementation(libs.testcontainers.gcloud)
  testImplementation(libs.testcontainers.junit.jupiter)
}

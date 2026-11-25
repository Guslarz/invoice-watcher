plugins {
  id("springboot.lib-conventions")
}

description = "Transactional attachment capture state in Firestore Datastore mode."

dependencies {
  testImplementation(testFixtures(projects.libs.testingTestcontainers))
  api(projects.libs.daoMailAttachmentApi)
  implementation(libs.google.cloud.datastore)
  implementation(libs.spring.boot.starter.validation)
  implementation(libs.jackson.databind)
  testImplementation(libs.testcontainers.gcloud)
  testImplementation(libs.testcontainers.junit.jupiter)
}

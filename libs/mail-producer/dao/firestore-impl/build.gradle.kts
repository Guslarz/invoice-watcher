plugins {
  id("spring.boot-lib-conventions")
}

description = "Storage of users in Firestore in Datastore mode."

dependencies {
  api(projects.libs.mailProducerDaoApi)

  implementation(libs.spring.boot.autoconfigure)
  implementation(libs.spring.cloud.gcp.starter.datastore)

  implementation(libs.commons.lang3)
  implementation(libs.jakarta.annotations)
}

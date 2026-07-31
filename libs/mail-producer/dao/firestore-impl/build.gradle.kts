plugins {
  id("spring-boot-lib-conventions")
}

dependencies {
  api(projects.libs.mailProducerDaoApi)

  implementation(libs.spring.boot.autoconfigure)
  implementation(libs.spring.cloud.gcp.starter.firestore)
}

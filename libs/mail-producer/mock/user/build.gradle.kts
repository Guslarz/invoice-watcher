plugins {
  id("spring-boot-lib-conventions")
}

dependencies {
  api(projects.libs.mailProducerDaoApi)
  api(projects.libs.secretApi)

  implementation(libs.spring.boot.autoconfigure)

  implementation(libs.jakarta.annotations)
}

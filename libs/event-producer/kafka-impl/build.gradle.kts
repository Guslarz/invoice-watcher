plugins {
  id("spring-boot-lib-conventions")
}

dependencies {
  api(projects.libs.eventProducerApi)

  implementation(libs.spring.boot.starter)
  implementation(libs.spring.kafka)
  implementation(libs.jackson.core)
  implementation(libs.jackson.databind)
}

plugins {
  id("spring-boot-lib-conventions")
}

dependencies {
  api(projects.libs.eventProducerApi)

  implementation(libs.spring.kafka)
}

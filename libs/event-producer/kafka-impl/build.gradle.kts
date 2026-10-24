plugins {
  id("spring.boot-lib-conventions")
}

description = "Event producer that sends events to Kafka."

dependencies {
  api(projects.libs.eventProducerApi)

  implementation(libs.spring.boot.starter)
  implementation(libs.spring.kafka)
  implementation(libs.jackson.core)
  implementation(libs.jackson.databind)
}

plugins {
  id("springboot.lib-conventions")
}

description = "Kafka transport and managed consumers for event processors."

dependencies {
  api(projects.libs.eventProcessorApi)
  implementation(libs.spring.boot.starter.validation)
  implementation(libs.spring.kafka)
  implementation(libs.micrometer.core)
  testImplementation(libs.testcontainers.kafka)
  testImplementation(libs.testcontainers.junit.jupiter)
}

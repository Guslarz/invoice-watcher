plugins {
  id("springboot.lib-conventions")
}

description = "Contracts for idempotent event processing and controlled replay."

dependencies {
  api(projects.libs.eventProducerApi)
  api(libs.jackson.databind)
  implementation(libs.spring.boot)
  implementation(libs.jakarta.validation.api)
}

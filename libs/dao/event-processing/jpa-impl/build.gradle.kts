plugins {
  id("springboot.lib-conventions")
}

description = "Isolated JPA persistence for processing lifecycle, outbox and replay with local H2 support."

dependencies {
  api(projects.libs.daoEventProcessingApi)
  api(projects.libs.secretApi)
  implementation(libs.spring.boot.starter.validation)
  implementation(libs.spring.data.jpa)
  implementation(libs.spring.orm)
  implementation(libs.hibernate.core)
  implementation(libs.hikari)
  implementation(libs.jackson.databind)
  runtimeOnly(libs.h2)
}

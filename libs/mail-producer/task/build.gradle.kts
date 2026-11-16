plugins {
  id("springboot.lib-conventions")
}

description = "Scan task: reads users, scans their folders and publishes new messages."

dependencies {
  api(projects.libs.mailConnectorApi)
  api(projects.libs.mailProducerSchedulerApi)
  api(projects.libs.daoUserConfigurationApi)
  api(projects.libs.eventProducerApi)

  implementation(libs.spring.boot.autoconfigure)
  implementation(libs.spring.boot.starter.validation)

  testImplementation(libs.spring.kafka)
  testImplementation(libs.jackson.databind)

  implementation(libs.slf4j.api)
}

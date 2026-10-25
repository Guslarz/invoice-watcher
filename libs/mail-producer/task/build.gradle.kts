plugins {
  id("spring.boot-lib-conventions")
}

description = "Scan task: reads users, scans their folders and publishes new messages."

dependencies {
  api(projects.libs.mailConnectorApi)
  api(projects.libs.mailProducerSchedulerApi)
  api(projects.libs.mailProducerDaoApi)
  api(projects.libs.eventProducerApi)

  implementation(libs.spring.boot.autoconfigure)
  implementation(libs.spring.boot.starter.validation)

  implementation(libs.slf4j.api)
}

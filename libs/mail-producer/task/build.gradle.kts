plugins {
  id("spring-boot-lib-conventions")
}

dependencies {
  api(projects.libs.mailConnectorApi)
  api(projects.libs.mailProducerSchedulerApi)
  api(projects.libs.mailProducerDaoApi)
  api(projects.libs.eventProducerApi)

  implementation(libs.spring.boot.autoconfigure)

  implementation(libs.slf4j.api)
}

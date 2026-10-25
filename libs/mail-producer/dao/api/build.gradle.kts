plugins {
  id("spring.boot-lib-conventions")
}

description = "Storage interfaces for users, mailbox configurations and folders."

dependencies {
  api(projects.libs.mailConnectorApi)

  implementation(libs.spring.boot.autoconfigure)
  implementation(libs.spring.boot.starter.validation)
}

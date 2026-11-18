plugins {
  id("springboot.lib-conventions")
}

description = "Storage interfaces for users, mailbox configurations and folders."

dependencies {
  api(projects.libs.mailConnectorApi)

  implementation(libs.spring.boot)
  implementation(libs.jakarta.validation.api)
}

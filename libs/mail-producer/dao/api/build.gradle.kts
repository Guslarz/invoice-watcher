plugins {
  id("spring-boot-lib-conventions")
}

dependencies {
  api(projects.libs.mailConnectorApi)

  implementation(libs.spring.boot.autoconfigure)
}

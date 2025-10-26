plugins {
  id("spring-boot-lib-conventions")
}

dependencies {
  api(projects.libs.mailConnectorApi)

  implementation(libs.jakarta.mail)

  testImplementation(libs.spring.boot.starter.test)
}

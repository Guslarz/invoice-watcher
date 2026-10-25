plugins {
  id("springboot.lib-conventions")
}

description = "Creates a mock user from properties at startup."

dependencies {
  api(projects.libs.mailProducerDaoApi)
  api(projects.libs.secretApi)

  implementation(libs.spring.boot.autoconfigure)
  implementation(libs.spring.boot.starter.validation)
}

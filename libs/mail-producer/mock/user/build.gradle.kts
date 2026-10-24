plugins {
  id("spring.boot-lib-conventions")
}

description = "Creates a mock user from properties at startup."

dependencies {
  api(projects.libs.mailProducerDaoApi)
  api(projects.libs.secretApi)

  implementation(libs.spring.boot.autoconfigure)

  implementation(libs.jakarta.annotations)
}

plugins {
  id("spring-boot-lib-conventions")
}

dependencies {
  api(projects.libs.secretApi)
  implementation(projects.libs.utilAes)

  implementation(libs.spring.boot.autoconfigure)
}

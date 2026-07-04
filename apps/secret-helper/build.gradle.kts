plugins {
  id("spring-boot-app-conventions")
}

dependencies {
  implementation(projects.libs.utilAes)

  implementation(libs.picocli)
}

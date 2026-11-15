plugins {
  id("springboot.lib-conventions")
}

description = "Google sign-in, Gmail consent and owner-scoped registration HTTP API."

dependencies {
  api(projects.libs.userRegistrationApi)

  implementation(libs.spring.boot.autoconfigure)
  implementation(libs.spring.tx)
  implementation(libs.spring.boot.starter.web)
  implementation(libs.spring.boot.starter.validation)
  implementation(libs.spring.boot.starter.security.oauth2.client)

  testImplementation(libs.spring.security.test)
}

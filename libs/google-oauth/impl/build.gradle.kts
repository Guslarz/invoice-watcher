plugins {
  id("springboot.lib-conventions")
}

description = "Google OAuth grant refresh and revocation with bounded network requests."

dependencies {
  api(projects.libs.googleOauthApi)

  implementation(libs.spring.boot.autoconfigure)
  implementation(libs.spring.boot.starter.validation)

  implementation(libs.jackson.databind)
}

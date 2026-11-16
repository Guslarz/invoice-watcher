plugins {
  id("springboot.lib-conventions")
}

description = "Registration contracts and public origin settings."

dependencies {
  api(projects.libs.oauthGoogleApi)

  implementation(libs.spring.boot)
  implementation(libs.spring.boot.starter.validation)
}

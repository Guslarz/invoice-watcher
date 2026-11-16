plugins {
  id("springboot.app-conventions")
}

description = "Registration backend with Google sign-in and Gmail OAuth."

dependencies {
  implementation(projects.libs.userRegistrationImpl)
  implementation(projects.libs.oauthGoogleImpl)
  implementation(projects.libs.daoUserConfigurationFirestoreImpl)
  implementation(projects.libs.daoUserConfigurationJpaImpl)
  implementation(projects.libs.secretAes)

  implementation(libs.spring.boot.starter.actuator)
  implementation(libs.spring.boot.starter.web)
}

plugins {
  id("springboot.lib-conventions")
}

description = "Google OAuth contracts and validated client settings."

dependencies {
  api(projects.libs.daoUserConfigurationApi)

  implementation(libs.spring.boot)
  implementation(libs.jakarta.validation.api)
}

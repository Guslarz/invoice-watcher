plugins {
  id("springboot.lib-conventions")
}

description = "Event producer interface and backend selection."

dependencies {
  implementation(libs.spring.boot)
  implementation(libs.jakarta.validation.api)
}

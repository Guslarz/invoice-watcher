plugins {
  id("springboot.lib-conventions")
}

description = "Processing state and recovery contracts."

dependencies {
  implementation(libs.spring.boot)
  implementation(libs.jakarta.validation.api)
}

plugins {
  id("springboot.lib-conventions")
}

description = "Event producer interface and backend selection."

dependencies {
  implementation(libs.spring.boot.autoconfigure)
  implementation(libs.spring.boot.starter.validation)
}

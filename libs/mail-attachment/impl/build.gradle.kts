plugins {
  id("springboot.lib-conventions")
}

description = "Bounded attachment capture, replay access and expiry orchestration."

dependencies {
  api(projects.libs.mailAttachmentApi)
  api(projects.libs.daoMailAttachmentApi)
  implementation(libs.spring.boot.starter.validation)
  implementation(libs.jackson.databind)
  implementation(libs.micrometer.core)
}

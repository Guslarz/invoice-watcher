plugins {
  id("springboot.lib-conventions")
}

description = "Attachment evidence, capture policy and bounded MIME source interfaces."

dependencies {
  api(projects.libs.attachmentStorageApi)
  implementation(libs.spring.boot)
  implementation(libs.jakarta.validation.api)
}

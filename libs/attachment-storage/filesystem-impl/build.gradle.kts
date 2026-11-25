plugins {
  id("springboot.lib-conventions")
}

description = "Private local attachment files with immutable generations and expiry cleanup."

dependencies {
  api(projects.libs.attachmentStorageApi)
  api(projects.libs.mailAttachmentApi)
  implementation(libs.spring.boot.starter.validation)
  implementation(libs.jackson.databind)
  testImplementation(projects.libs.attachmentStorageGcsImpl)
}

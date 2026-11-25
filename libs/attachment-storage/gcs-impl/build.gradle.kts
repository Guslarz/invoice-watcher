plugins {
  id("springboot.lib-conventions")
}

description = "Generation-pinned private attachment storage in Google Cloud Storage."

dependencies {
  api(projects.libs.attachmentStorageApi)
  api(projects.libs.mailAttachmentApi)
  implementation(libs.google.cloud.storage)
  implementation(libs.spring.boot.starter.validation)
}

plugins {
  id("springboot.lib-conventions")
}

description = "Isolated JPA persistence for attachment captures with local H2 support."

dependencies {
  testImplementation(projects.libs.daoUserConfigurationJpaImpl)
  testImplementation(projects.libs.daoEventProcessingJpaImpl)
  testImplementation(projects.libs.daoMailAttachmentFirestoreImpl)
  testImplementation(projects.libs.daoEventProcessingFirestoreImpl)
  testImplementation(projects.libs.attachmentStorageFilesystemImpl)
  testImplementation(projects.libs.attachmentStorageGcsImpl)

  api(projects.libs.daoMailAttachmentApi)
  api(projects.libs.secretApi)
  implementation(libs.spring.boot.starter.validation)
  implementation(libs.spring.data.jpa)
  implementation(libs.spring.orm)
  implementation(libs.hibernate.core)
  implementation(libs.hikari)
  implementation(libs.jackson.databind)
  runtimeOnly(libs.h2)
}

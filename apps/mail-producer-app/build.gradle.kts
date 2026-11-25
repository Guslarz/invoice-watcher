plugins {
  id("springboot.app-conventions")
}

description = "Spring Boot application that scans mailboxes on a schedule and publishes their messages as events."

dependencies {
  implementation(projects.libs.mailConnectorImpl)
  implementation(projects.libs.mailAttachmentImpl)
  implementation(projects.libs.attachmentStorageGcsImpl)
  implementation(projects.libs.attachmentStorageFilesystemImpl)
  implementation(projects.libs.daoMailAttachmentFirestoreImpl)
  implementation(projects.libs.daoMailAttachmentJpaImpl)
  implementation(projects.libs.mailProducerSchedulerImpl)
  implementation(projects.libs.mailProducerTask)
  implementation(projects.libs.daoUserConfigurationJpaImpl)
  implementation(projects.libs.daoUserConfigurationFirestoreImpl)
  implementation(projects.libs.mailProducerMockUser)
  implementation(projects.libs.secretAes)
  implementation(projects.libs.oauthGoogleImpl)
  implementation(projects.libs.eventProducerKafkaImpl)

  implementation(libs.spring.boot.starter.actuator)
  implementation(libs.spring.boot.starter.web)
}

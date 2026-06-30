plugins {
  id("spring-boot-app-conventions")
}

dependencies {
  implementation(projects.libs.mailConnectorImpl)
  implementation(projects.libs.mailProducerSchedulerImpl)
  implementation(projects.libs.mailProducerTask)
  implementation(projects.libs.mailProducerDaoJpa)
}

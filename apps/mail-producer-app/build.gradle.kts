plugins {
  id("spring-boot-app-conventions")
}

dependencies {
  implementation(projects.libs.mailConnectorImpl)
  implementation(projects.libs.mailProducerSchedulerImpl)
  implementation(projects.libs.mailProducerTask)
  implementation(projects.libs.mailProducerDaoJpaImpl)
  implementation(projects.libs.mailProducerMockUser)
  implementation(projects.libs.secretAes)
  implementation(projects.libs.eventProducerKafkaImpl)

  implementation(libs.spring.boot.starter.actuator)
  implementation(libs.spring.boot.starter.web)
}

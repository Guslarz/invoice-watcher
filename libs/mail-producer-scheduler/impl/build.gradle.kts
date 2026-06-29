plugins {
  id("spring-boot-lib-conventions")
}

dependencies {
  api(projects.libs.mailProducerSchedulerApi)

  implementation(libs.spring.boot.autoconfigure)

  implementation(libs.jakarta.annotations)
}

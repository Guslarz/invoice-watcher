plugins {
  id("spring.boot-lib-conventions")
}

description = "Runs the mail producer task at a fixed rate."

dependencies {
  api(projects.libs.mailProducerSchedulerApi)

  implementation(libs.spring.boot.autoconfigure)

  implementation(libs.jakarta.annotations)
}

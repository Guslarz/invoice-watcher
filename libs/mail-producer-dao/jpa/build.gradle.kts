plugins {
  id("spring-boot-lib-conventions")
}

dependencies {
  api(projects.libs.mailProducerDaoApi)

  implementation(libs.spring.boot.starter.data.jpa)
  implementation(libs.spring.cloud.gcp.starter.sql.postgresql)

  runtimeOnly(libs.h2)
}

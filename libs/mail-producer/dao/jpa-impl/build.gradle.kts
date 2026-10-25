plugins {
  id("springboot.lib-conventions")
}

description = "Storage of users in a relational database via JPA (H2 or Cloud SQL)."

dependencies {
  api(projects.libs.mailProducerDaoApi)

  implementation(libs.spring.boot.starter.data.jpa)
  implementation(libs.spring.boot.starter.validation)
  implementation(libs.spring.cloud.gcp.starter.sql.postgresql)

  implementation(libs.jakarta.annotations)

  runtimeOnly(libs.h2)
}

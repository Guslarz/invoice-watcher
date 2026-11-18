plugins {
  id("springboot.lib-conventions")
  id("jvm.h2-tools-conventions")
}

description = "Storage of users in a relational database via JPA (H2 or Cloud SQL)."

dependencies {
  api(projects.libs.daoUserConfigurationApi)

  implementation(libs.spring.data.jpa)
  implementation(libs.spring.orm)
  implementation(libs.hibernate.core)
  implementation(libs.hikari)
  implementation(libs.gax)
  implementation(libs.google.auth.oauth2.http)
  implementation(libs.postgresql.socket.factory)
  implementation(libs.spring.boot.starter.validation)
  runtimeOnly(libs.postgresql)

  implementation(libs.jakarta.annotations)

  runtimeOnly(libs.h2)
}

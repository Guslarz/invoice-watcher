package spring

import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
  id("jvm.common-conventions")
  id("io.spring.dependency-management")
}

val libs = versionCatalogs.named("libs")

dependencyManagement {
  imports {
    mavenBom(libs.findLibrary("spring-cloud-gcp-dependencies").get().get().toString())
    // Last, so its versions win over the ones the GCP BOM manages too.
    mavenBom(SpringBootPlugin.BOM_COORDINATES)
  }
}

dependencies {
  annotationProcessor(libs.findLibrary("spring-boot-configuration-processor").get())

  testImplementation(libs.findLibrary("spring-boot-starter-test").get())
  // Gradle 9 no longer provides it; the Spring Boot plugin adds it only to applications.
  testRuntimeOnly(libs.findLibrary("junit-platform-launcher").get())
}

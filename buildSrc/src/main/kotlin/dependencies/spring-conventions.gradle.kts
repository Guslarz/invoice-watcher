package dependencies

plugins {
  id("io.spring.dependency-management")
}

dependencyManagement {
  imports {
    mavenBom("com.google.cloud:spring-cloud-gcp-dependencies:8.0.4") // Can't pass it any other way
  }
}


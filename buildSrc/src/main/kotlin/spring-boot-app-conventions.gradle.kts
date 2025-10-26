plugins {
  id("java-app-conventions")
  id("dependencies.spring-conventions")
  id("org.springframework.boot")
}

dependencies {
  implementation("org.springframework.boot:spring-boot-starter")
  annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

  testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks {
  withType<Test> {
    useJUnitPlatform()
  }
}

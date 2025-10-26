plugins {
  id("java-lib-conventions")
  id("dependencies.spring-conventions")
  id("org.springframework.boot") apply false
}

dependencies {
  annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
}

tasks {
  withType<Test> {
    useJUnitPlatform()
  }

  named("compileJava") {
    inputs.files(tasks.named("processResources"))
  }

  named("bootJar") {
    enabled = false
  }

  named("jar") {
    enabled = true
  }
}

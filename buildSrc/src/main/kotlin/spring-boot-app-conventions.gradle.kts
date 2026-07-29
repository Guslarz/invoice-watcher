import docker.DockerImageResourceType
import docker.DockerImageResourceCategoryName

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

configurations.consumable("dockerImageResource") {
  attributes {
    attribute(
      Category.CATEGORY_ATTRIBUTE,
      objects.named(Category::class.java, DockerImageResourceCategoryName)
    )
    attribute(DockerImageResourceType.Attr, DockerImageResourceType.Jar)
  }
  outgoing.artifact(tasks.named("bootJar"))
}

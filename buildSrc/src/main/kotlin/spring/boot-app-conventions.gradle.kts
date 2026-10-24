package spring

import docker.DockerImageResourceCategoryName
import docker.DockerImageResourceType

plugins {
  id("spring.boot-common-conventions")
  id("org.springframework.boot")
}

dependencies {
  implementation(versionCatalogs.named("libs").findLibrary("spring-boot-starter").get())
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

package springboot

import docker.DockerImageResourceCategoryName
import docker.DockerImageResourceType

plugins {
  id("springboot.common-conventions")
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

// Applications carry one deterministic view of the mode hints contributed by their libraries.
val metadataClasspath = objects.newInstance(MetadataClasspath::class.java)
metadataClasspath.files.from(configurations.named("runtimeClasspath"))
tasks.named<JavaCompile>("compileJava") {
  options.compilerArgumentProviders.add(metadataClasspath)
  doFirst(ClearApplicationMetadata())
  doLast(ApplicationMetadata())
}

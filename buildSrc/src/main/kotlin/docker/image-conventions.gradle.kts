package docker

import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.DockerSaveImage
import org.apache.tools.ant.filters.ReplaceTokens
import org.gradle.api.attributes.Category
import org.gradle.api.tasks.Copy
import org.gradle.kotlin.dsl.filter
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.register

plugins {
  id("com.bmuschko.docker-remote-api")
}

val resourcesDir = layout.buildDirectory.dir("resources")
val image = "${name}:${version}"
val imageFile = layout.buildDirectory.file("${name}-${version}.tar.gz")

val dockerImageResource = configurations.dependencyScope("dockerImageResource")
val resolvableDockerImageResource = configurations.resolvable("resolvableDockerImageResource") {
  extendsFrom(dockerImageResource)
  attributes {
    attribute(
      Category.CATEGORY_ATTRIBUTE,
      objects.named(Category::class.java, DockerImageResourceCategoryName)
    )
  }
}

tasks {
  val copySources = register<Copy>("copySources") {
    from("src") {
      filter<ReplaceTokens>("tokens" to mapOf(
        "PROJECT_VERSION" to version
      ))
    }
    into(resourcesDir)
  }

  val copyDockerImageResource = register<Copy>("copyDockerImageResource") {
    from(resolvableDockerImageResource)
    into(resourcesDir)
  }

  val buildImage = register<DockerBuildImage>("buildImage") {
    dependsOn(copySources, copyDockerImageResource)
    inputDir.set(resourcesDir)
    images.add(image)
  }

  val saveImage = register<DockerSaveImage>("saveImage") {
    dependsOn(buildImage)
    images.add(image)
    destFile.set(imageFile)
  }

  register("build") {
    dependsOn(saveImage)
    outputs.file(imageFile)
  }
}

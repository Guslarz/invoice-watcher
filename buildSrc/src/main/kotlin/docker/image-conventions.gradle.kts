package docker

import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.DockerPushImage
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

val propertyPrefix = "com.guslarz.invoicewatcher.docker"
val dockerProperties = providers.gradlePropertiesPrefixedBy(propertyPrefix).map {
  it.mapKeys { (key, _) -> key.removePrefix("$propertyPrefix.") }
}
val registryProperty = dockerProperties.map { it["registry"] }

val resourcesDir = layout.buildDirectory.dir("resources")
val image = dockerProperties.map { "${it["registry"]?.plus("/") ?: "" }${it["namespace"]?.plus("/") ?: ""}${name}:${version}" }
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

docker {
  if (registryProperty.isPresent) {
    registryCredentials {
      url = registryProperty
      username = dockerProperties.map { it["username"] }
      password = dockerProperties.map { it["password"] }
    }
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

  val dockerBuildImage = register<DockerBuildImage>("dockerBuildImage") {
    dependsOn(copySources, copyDockerImageResource)
    images.add(image)
    inputDir.set(resourcesDir)
  }

  val dockerSaveImage = register<DockerSaveImage>("dockerSaveImage") {
    dependsOn(dockerBuildImage)
    images.add(image)
    destFile.set(imageFile)
  }

  val dockerPushImage = register<DockerPushImage>("dockerPushImage") {
    dependsOn(dockerSaveImage)
    images.add(image)
  }

  register("build") {
    dependsOn(dockerSaveImage)
  }

  register("publish") {
    if (registryProperty.isPresent) {
      dependsOn(dockerPushImage)
    }
  }
}

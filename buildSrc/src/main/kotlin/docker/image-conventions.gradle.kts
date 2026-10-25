package docker

import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.DockerPushImage
import org.gradle.api.attributes.Category
import org.gradle.api.tasks.Copy
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.of
import org.gradle.kotlin.dsl.register

plugins {
  base
  id("com.bmuschko.docker-remote-api")
}

val propertyPrefix = "com.guslarz.invoicewatcher.docker"
val dockerProperties = providers.gradlePropertiesPrefixedBy(propertyPrefix).map {
  it.mapKeys { (key, _) -> key.removePrefix("$propertyPrefix.") }
}
val registryProperty = dockerProperties.map { it["registry"] }

val resourcesDir = layout.buildDirectory.dir("resources")
val imageName = name
val projectVersion = providers.provider { version.toString() }
val imageVersion = dockerProperties.zip(projectVersion) { properties, version ->
  properties["tag"] ?: version
}
val image = dockerProperties.zip(imageVersion) { properties, tag ->
  "${properties["registry"]?.plus("/") ?: ""}${properties["namespace"]?.plus("/") ?: ""}$imageName:$tag"
}

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
    from("src")
    into(resourcesDir)
  }

  // Dockerfiles copy the application jar as app.jar, independent of the app's name and version.
  val copyDockerImageResource = register<Copy>("copyDockerImageResource") {
    from(resolvableDockerImageResource)
    into(resourcesDir)
    rename(".*\\.jar", "app.jar")
  }

  val dockerBuildImage = register<DockerBuildImage>("dockerBuildImage") {
    dependsOn(copySources, copyDockerImageResource)
    images.add(image)
    inputDir.set(resourcesDir)
    buildArgs.put("PROJECT_VERSION", projectVersion)
    buildArgs.put("BUILD_DATE", providers.of(BuildTimeValueSource::class) {})
  }

  val dockerPushImage = register<DockerPushImage>("dockerPushImage") {
    dependsOn(dockerBuildImage)
    images.add(image)
  }

  assemble {
    dependsOn(dockerBuildImage)
  }

  register("publish") {
    if (registryProperty.isPresent) {
      dependsOn(dockerPushImage)
    }
  }
}

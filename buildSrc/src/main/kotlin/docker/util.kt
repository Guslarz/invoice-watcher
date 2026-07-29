package docker

import org.gradle.api.attributes.Attribute

enum class DockerImageResourceType {
  Jar,
  Statics;

  companion object {
    val Attr = Attribute.of(
      "com.guslarz.invoicewatcher.docker-image-resource",
      DockerImageResourceType::class.java
    )
  }
}

const val DockerImageResourceCategoryName = "dockerImageResource"

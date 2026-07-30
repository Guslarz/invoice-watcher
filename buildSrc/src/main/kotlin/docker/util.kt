package docker

import org.gradle.api.attributes.Attribute

enum class DockerImageResourceType {
  Jar,
  Statics;

  companion object {
    val Attr = Attribute.of(
      "com.guslarz.invoicewatcher.DockerImageResource",
      DockerImageResourceType::class.java
    )
  }
}

const val DockerImageResourceCategoryName = "dockerImageResource"

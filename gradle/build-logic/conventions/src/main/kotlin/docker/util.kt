package docker

import org.gradle.api.attributes.Attribute
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import java.time.Instant

enum class DockerImageResourceType {
  Jar,
  // Reserved for images of static files (e.g. a frontend); no image consumes it yet.
  Statics;

  companion object {
    val Attr = Attribute.of(
      "com.guslarz.invoicewatcher.DockerImageResource",
      DockerImageResourceType::class.java
    )
  }
}

const val DockerImageResourceCategoryName = "dockerImageResource"

// The current UTC time (RFC 3339), obtained when the build runs rather than when it is configured.
abstract class BuildTimeValueSource : ValueSource<String, ValueSourceParameters.None> {
  override fun obtain(): String = Instant.now().toString()
}

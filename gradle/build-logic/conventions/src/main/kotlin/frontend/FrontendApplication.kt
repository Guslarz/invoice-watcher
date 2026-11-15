package frontend

import org.gradle.api.provider.Property

abstract class FrontendApplication {
  abstract val angularProject: Property<String>
  abstract val outputPath: Property<String>
  abstract val playwrightConfig: Property<String>
}

package configurations.docker

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration

class DockerConfigurationPlugin : Plugin<Project> {

  override fun apply(target: Project) {
    target.extensions.create(
      DockerConfigurationExtension.Name,
      DockerConfigurationExtension::class.java,
      target
    )
  }
}

val Project.dockerConfiguration: DockerConfigurationExtension
  get() = extensions
    .getByName(DockerConfigurationExtension.Name) as DockerConfigurationExtension

val Project.dockerImage: Configuration
  get() = dockerConfiguration.configuration

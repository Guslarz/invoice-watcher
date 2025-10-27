package configurations.docker

import org.gradle.api.Project
import org.gradle.api.Action
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Attribute

open class DockerConfigurationExtension(private val p: Project) {
  companion object {
    val Name = "dockerConfiguration"
    val ProducerName = "dockerImage"
    val ConsumerName = "dockerImage"
    val ConfigurationCategory = "dockerImage"
  }

  private var _configuration: Configuration? = null

  val configuration: Configuration 
    get() = _configuration ?: throw RuntimeException()

  fun configureProducer() = configure(ProducerName) {
    isCanBeConsumed = true
    isCanBeResolved = false
    attributes {
      attribute(
        Category.CATEGORY_ATTRIBUTE, 
        p.objects.named(Category::class.java, ConfigurationCategory)
      )
    }
  }

  fun configureConsumer() = configure(ConsumerName) {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
      attribute(
        Category.CATEGORY_ATTRIBUTE, 
        p.objects.named(Category::class.java, ConfigurationCategory)
      )
    }
  }

  private fun configure(name: String, action: Action<Configuration>): Configuration {
    val configuration = p.configurations.create(name, action)
    _configuration = configuration
    return configuration
  }
}

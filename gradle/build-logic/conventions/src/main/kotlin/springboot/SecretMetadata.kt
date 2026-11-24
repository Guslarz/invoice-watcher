package springboot

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.io.File
import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.process.CommandLineArgumentProvider

abstract class AdditionalMetadata : CommandLineArgumentProvider {
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val files: ConfigurableFileCollection

  override fun asArguments(): Iterable<String> = additionalMetadataArguments(files.files)
}

internal fun additionalMetadataArguments(files: Collection<File>): List<String> {
  if (files.isEmpty()) return emptyList()
  val roots = files.map { it.parentFile.parentFile.absolutePath }.distinct().sorted()
  return listOf("-Aorg.springframework.boot.configurationprocessor.additionalMetadataLocations=" + roots.joinToString(","))
}

// IntelliJ reads source declarations; Spring's processor preserves the Java type in its output.
class SecretMetadata : Action<Task> {
  override fun execute(task: Task) {
    val compile = task as JavaCompile
    rewriteSecretMetadata(
      compile.destinationDirectory.file("META-INF/spring-configuration-metadata.json").get().asFile,
      compile.options.compilerArgumentProviders.filterIsInstance<AdditionalMetadata>().single().files.files
    )
  }
}

internal fun rewriteSecretMetadata(file: File, additionalMetadata: Collection<File> = emptyList()) {
  if (!file.isFile) return
  val metadata = JsonSlurper().parse(file) as Map<*, *>
  val properties = metadata["properties"] as? List<*> ?: return
  val declarations = additionalMetadata.flatMap { source ->
    val data = JsonSlurper().parse(source) as Map<*, *>
    (data["properties"] as? List<*>)?.map { it as Map<*, *> } ?: emptyList()
  }
  val scalars = declarations.filter { it["type"] == "java.lang.String" }.map { it["name"] }.toSet()
  val secrets = properties.map { it as Map<*, *> }
    .filter { it["type"] == "com.guslarz.invoicewatcher.secret.api.Secret" }
  val missing = secrets.map { it["name"] }.filterNot { it in scalars }.sortedBy { it.toString() }
  require(missing.isEmpty()) {
    "Declare Secret properties as java.lang.String in source resource " +
      "META-INF/additional-spring-configuration-metadata.json: " + missing.joinToString(", ")
  }
  for (secret in secrets) {
    @Suppress("UNCHECKED_CAST")
    (secret as MutableMap<String, Any?>)["type"] = "java.lang.String"
  }
  if (secrets.isNotEmpty()) file.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(metadata)) + "\n")
}

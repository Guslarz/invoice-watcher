package springboot

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.io.File
import java.net.URLClassLoader
import java.util.zip.ZipFile
import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.process.CommandLineArgumentProvider

private const val metadataPath = "META-INF/spring-configuration-metadata.json"

open class MetadataClasspath @Inject constructor(objects: ObjectFactory) : CommandLineArgumentProvider {
  @get:Classpath
  val files: ConfigurableFileCollection = objects.fileCollection()
  override fun asArguments(): Iterable<String> = emptyList()
}

class ImplementationMetadata : Action<Task> {
  override fun execute(task: Task) {
    val compile = task as JavaCompile
    implementationMetadata(compile.destinationDirectory.get().asFile, compile.classpath.files)
  }
}

class ClearApplicationMetadata : Action<Task> {
  override fun execute(task: Task) {
    val compile = task as JavaCompile
    compile.destinationDirectory.file(metadataPath).get().asFile.delete()
  }
}

class ApplicationMetadata : Action<Task> {
  override fun execute(task: Task) {
    val compile = task as JavaCompile
    val own = compile.destinationDirectory.file(metadataPath).get().asFile
    val inputs = mutableListOf<Map<String, Any?>>()
    if (own.isFile) inputs.add(readMetadata(own.readText()))
    for (file in compile.options.compilerArgumentProviders.filterIsInstance<MetadataClasspath>().single().files.files.sorted()) {
      if (file.isDirectory) {
        val metadata = file.resolve(metadataPath)
        if (metadata.isFile) inputs.add(readMetadata(metadata.readText()))
      } else if (file.extension == "jar") {
        ZipFile(file).use { jar ->
          jar.getEntry(metadataPath)?.let { entry ->
            inputs.add(readMetadata(jar.getInputStream(entry).bufferedReader().use { it.readText() }))
          }
        }
      }
    }
    val merged = mergeMetadata(inputs)
    if (merged.values.any { (it as List<*>).isNotEmpty() }) writeMetadata(own, merged)
  }
}

@Suppress("UNCHECKED_CAST")
internal fun implementationMetadata(directory: File, classpath: Collection<File>) {
  if (!directory.isDirectory) return
  val file = directory.resolve(metadataPath)
  val original = if (file.isFile) readMetadata(file.readText()).toMutableMap() else mutableMapOf()
  val hints = ((original["hints"] as? List<Map<String, Any?>>) ?: emptyList())
    .filterNot { (it["name"] as? String)?.endsWith(".mode") == true }.toMutableList()
  val urls = (listOf(directory) + classpath).map { it.toURI().toURL() }.toTypedArray()
  URLClassLoader(urls, ClassLoader.getPlatformClassLoader()).use { loader ->
    for (type in directory.walkTopDown().filter { it.extension == "class" && it.name !in setOf("module-info.class", "package-info.class") }.sortedBy { it.path }) {
      val name = type.relativeTo(directory).path.removeSuffix(".class").replace(File.separatorChar, '.')
      val clazz = Class.forName(name, false, loader)
      val conditions = clazz.declaredAnnotations.flatMap { annotation ->
        when (annotation.annotationClass.java.name) {
          "org.springframework.boot.autoconfigure.condition.ConditionalOnProperty" -> listOf(annotation)
          "org.springframework.boot.autoconfigure.condition.ConditionalOnProperties" ->
            (annotation.annotationClass.java.getMethod("value").invoke(annotation) as Array<Annotation>).toList()
          else -> emptyList()
        }
      }
      for (condition in conditions) {
        val annotation = condition.annotationClass.java
        val prefix = annotation.getMethod("prefix").invoke(condition) as String
        val having = annotation.getMethod("havingValue").invoke(condition) as String
        val names = (annotation.getMethod("name").invoke(condition) as Array<*>).ifEmpty {
          annotation.getMethod("value").invoke(condition) as Array<*>
        }
        for (property in names) {
          val key = (prefix.trimEnd('.') + "." + property).trimStart('.')
          if (key.endsWith(".mode") && having.isNotBlank()) {
            hints.add(mapOf("name" to key, "values" to listOf(mapOf("value" to having))))
          }
        }
      }
    }
  }
  if (hints.isNotEmpty() || original.containsKey("hints")) {
    original["hints"] = mergeMetadata(listOf(mapOf("hints" to hints)))["hints"]
  }
  if (original.isNotEmpty()) writeMetadata(file, original)
}

@Suppress("UNCHECKED_CAST")
internal fun readMetadata(text: String): Map<String, Any?> = JsonSlurper().parseText(text) as Map<String, Any?>

internal fun mergeMetadata(inputs: List<Map<String, Any?>>): Map<String, Any?> {
  val properties = sortedMapOf<String, Map<String, Any?>>()
  val groups = sortedMapOf<String, Map<String, Any?>>()
  val hints = sortedMapOf<String, MutableMap<String, Any?>>()
  for (metadata in inputs) {
    for (item in items(metadata, "groups")) {
      if (owned(item)) groups[JsonOutput.toJson(item)] = item
    }
    for (item in items(metadata, "properties")) {
      if (!owned(item)) continue
      val name = item["name"] as String
      val previous = properties[name]
      if (previous != null) {
        for (field in listOf("type", "defaultValue", "description", "deprecation")) {
          require(previous[field] == item[field]) { "Conflicting metadata for $name ($field)" }
        }
      } else properties[name] = item
    }
    for (item in items(metadata, "hints")) {
      if (!owned(item)) continue
      val name = item["name"] as String
      val hint = hints.getOrPut(name) { mutableMapOf("name" to name) }
      for (kind in listOf("values", "providers")) {
        val merged = sortedMapOf<String, Map<String, Any?>>()
        for (entry in items(hint, kind) + items(item, kind)) {
          val key = if (kind == "values") JsonOutput.toJson(entry["value"]) else JsonOutput.toJson(entry)
          val previous = merged.putIfAbsent(key, entry)
          require(previous == null || previous == entry) { "Conflicting value hints for $name" }
        }
        if (merged.isNotEmpty()) hint[kind] = merged.values.toList()
      }
    }
  }
  return mapOf("groups" to groups.values.toList(), "properties" to properties.values.toList(), "hints" to hints.values.toList())
}

@Suppress("UNCHECKED_CAST")
private fun items(metadata: Map<String, Any?>, key: String): List<Map<String, Any?>> =
  metadata[key] as? List<Map<String, Any?>> ?: emptyList()

private fun owned(item: Map<String, Any?>): Boolean = (item["name"] as? String)?.startsWith("invoice-watcher.") == true

private fun writeMetadata(file: File, metadata: Map<String, Any?>) {
  file.parentFile.mkdirs()
  file.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(metadata)) + "\n")
}

package springboot

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.io.File
import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.tasks.compile.JavaCompile

// The binder converts encrypted strings to Secret; IDEs need the external scalar type.
class SecretMetadata : Action<Task> {
  override fun execute(task: Task) {
    val compile = task as JavaCompile
    rewriteSecretMetadata(
      compile.destinationDirectory.file("META-INF/spring-configuration-metadata.json").get().asFile
    )
  }
}

internal fun rewriteSecretMetadata(file: File) {
  if (!file.isFile) return
  val metadata = JsonSlurper().parse(file) as Map<*, *>
  val properties = metadata["properties"] as? List<*> ?: return
  var changed = false
  for (entry in properties) {
    @Suppress("UNCHECKED_CAST")
    val property = entry as MutableMap<String, Any?>
    if (property["type"] == "com.guslarz.invoicewatcher.secret.api.Secret") {
      property["type"] = "java.lang.String"
      changed = true
    }
  }
  if (changed) file.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(metadata)) + "\n")
}

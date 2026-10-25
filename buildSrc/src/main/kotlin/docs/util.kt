package docs

import groovy.json.JsonOutput
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

// Facts about one module for the generated documentation reference (scripts/gen-reference.py).
abstract class DocsModuleFacts : DefaultTask() {
  @get:Input
  abstract val modulePath: Property<String>

  @get:Input
  abstract val moduleDir: Property<String>

  @get:Input
  @get:Optional
  abstract val moduleDescription: Property<String>

  @get:Input
  abstract val moduleDependencies: ListProperty<String>

  @get:OutputFile
  abstract val output: RegularFileProperty

  @TaskAction
  fun write() {
    val facts = mapOf(
      "path" to modulePath.get(),
      "dir" to moduleDir.get(),
      "description" to moduleDescription.orNull,
      "dependencies" to moduleDependencies.get()
    )
    output.get().asFile.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(facts)))
  }
}

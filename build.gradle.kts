plugins {
  alias(libs.plugins.axion.release)
}

allprojects {
  group = "com.guslarz.invoice-watcher"
  version = rootProject.scmVersion.version
}

// Facts the generated documentation reference is built from (scripts/gen-reference.py):
// modules with their descriptions and dependencies, configuration metadata (compiled classes)
// and the secret-helper usage texts.
val docsModules = objects.property<String>()

val docsReference = tasks.register("docsReference") {
  group = "documentation"
  description = "Collects the inputs of the generated documentation reference."
  val output = layout.buildDirectory.file("docs-reference/modules.json")
  inputs.property("modules", docsModules)
  outputs.file(output)
  dependsOn(":apps:secret-helper-app:cliReference")
  doLast {
    output.get().asFile.writeText(docsModules.get())
  }
}

gradle.projectsEvaluated {
  val moduleConfigurations =
    setOf("api", "implementation", "compileOnly", "runtimeOnly", "dockerImageResource")
  docsModules.set(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(
    subprojects.filter { it.buildFile.exists() }.sortedBy { it.path }.map { module ->
      mapOf(
        "path" to module.path,
        "dir" to module.projectDir.relativeTo(rootDir).path,
        "description" to module.description,
        "dependencies" to module.configurations
          .filter { it.name in moduleConfigurations }
          .flatMap { it.dependencies.withType<ProjectDependency>() }
          .map { it.path }
          .distinct()
          .sorted()
      )
    }
  )))
  docsReference.configure {
    dependsOn(subprojects.mapNotNull { it.tasks.findByName("classes") })
  }
}

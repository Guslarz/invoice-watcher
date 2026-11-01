package docs

// Every module writes its own facts for the generated documentation reference (scripts/gen-reference.py),
// so no project reads another one: `./gradlew docsReference` runs the task in all modules.
val moduleConfigurations =
  setOf("api", "implementation", "compileOnly", "runtimeOnly", "dockerImageResource")

val docsModuleFacts = tasks.register<DocsModuleFacts>("docsModuleFacts") {
  modulePath = project.path
  moduleDir = projectDir.relativeTo(isolated.rootProject.projectDirectory.asFile).path
  // Module build files set the description after the convention plugins are applied.
  moduleDescription = provider { project.description }
  moduleDependencies = provider {
    configurations
      .filter { it.name in moduleConfigurations }
      .flatMap { it.dependencies.withType<ProjectDependency>() }
      .map { it.path }
      .distinct()
      .sorted()
  }
  output = layout.buildDirectory.file("docs-reference/module.json")
}

val docsReference = tasks.register("docsReference") {
  group = "documentation"
  description = "Collects the inputs of the generated documentation reference."
  dependsOn(docsModuleFacts)
}

// The configuration metadata is read from the compiled classes.
pluginManager.withPlugin("java") {
  docsReference.configure { dependsOn(tasks.named("classes")) }
}

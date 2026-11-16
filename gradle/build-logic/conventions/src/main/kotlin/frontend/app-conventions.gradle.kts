package frontend

import docker.DockerImageResourceCategoryName
import docker.DockerImageResourceType

plugins {
  base
  id("docs.reference-conventions")
}

val frontendDir = isolated.rootProject.projectDirectory.dir("frontend")
val frontendApplication = extensions.create<FrontendApplication>("frontendApplication")
frontendApplication.playwrightConfig.convention("playwright.config.ts")
val nodeTools = configurations.create("frontendNodeRuntime") {
  isCanBeConsumed = false
  isCanBeResolved = true
}
val pnpmTools = configurations.create("frontendPnpmRuntime") {
  isCanBeConsumed = false
  isCanBeResolved = true
}
val browserRuntime = configurations.create("frontendBrowserRuntime") {
  isCanBeConsumed = false
  isCanBeResolved = true
}
dependencies {
  add(nodeTools.name, project(mapOf("path" to ":frontend", "configuration" to "frontendNode")))
  add(pnpmTools.name, project(mapOf("path" to ":frontend", "configuration" to "frontendPnpm")))
  add(browserRuntime.name, project(mapOf("path" to ":frontend", "configuration" to "frontendBrowserImage")))
}
tasks.withType<ManagedPnpmTask>().configureEach {
  nodeRuntime.from(nodeTools)
  pnpmRuntime.from(pnpmTools)
  workspaceDirectory.set(frontendDir)
}
val frontendBuild = tasks.register<ManagedPnpmTask>("frontendBuild") {
  dependsOn(":frontend:frontendUiBuild")
  arguments.set(frontendApplication.angularProject.map { listOf("exec", "ng", "build", it) })
  inputs.files(fileTree(frontendDir) {
    exclude("**/node_modules/**", "**/dist/**", "**/.gradle/**", "**/build/**",
        ".angular/**", "out-tsc/**", "test-results/**", "playwright-report/**")
  })
  outputs.dir(frontendApplication.outputPath.map { frontendDir.dir(it) })
}
tasks.withType<BrowserContainerTask>().configureEach {
  image.from(browserRuntime)
  workspaceDirectory.set(frontendDir)
  reportsDirectory.set(layout.buildDirectory.dir("playwright"))
  playwrightConfig.set(frontendApplication.playwrightConfig)
}
val frontendBrowserCheck = tasks.register<BrowserContainerTask>("frontendBrowserCheck") {
  dependsOn(frontendBuild, ":frontend:frontendCatalogueBuild")
}
tasks.register<ManagedPnpmTask>("frontendServe") {
  dependsOn(":frontend:frontendUiBuild")
  arguments.set(frontendApplication.angularProject.map {
    listOf("exec", "ng", "serve", it, "--host", "127.0.0.1", "--proxy-config", "proxy.json")
  })
}
tasks.register<BrowserContainerTask>("frontendUpdateSnapshots") {
  dependsOn(frontendBuild, ":frontend:frontendCatalogueBuild")
  updateSnapshots.set(true)
}
tasks.named("assemble") { dependsOn(frontendBuild) }
tasks.named("check") { dependsOn(":frontend:check", frontendBrowserCheck) }
configurations.consumable("dockerImageResource") {
  attributes {
    attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class.java, DockerImageResourceCategoryName))
    attribute(DockerImageResourceType.Attr, DockerImageResourceType.Statics)
  }
  outgoing.artifact(frontendApplication.outputPath.map { frontendDir.dir(it) }) { builtBy(frontendBuild) }
}

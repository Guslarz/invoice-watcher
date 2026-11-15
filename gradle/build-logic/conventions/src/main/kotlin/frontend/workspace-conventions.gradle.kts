package frontend

import com.github.gradle.node.pnpm.task.PnpmTask
import com.github.gradle.node.task.NodeSetupTask

plugins {
  base
  id("com.github.node-gradle.node")
  id("docs.reference-conventions")
}

val frontendDir = layout.projectDirectory
val frontendPackage = providers.fileContents(frontendDir.file("package.json")).asText
val pinnedNode = providers.fileContents(frontendDir.file(".node-version")).asText.map { it.trim() }
val pinnedPnpm = frontendPackage.map { text ->
  Regex("\"packageManager\"\\s*:\\s*\"pnpm@([0-9]+\\.[0-9]+\\.[0-9]+)\"")
      .find(text)?.groupValues?.get(1)
      ?: error("frontend/package.json must pin pnpm through packageManager")
}

node {
  download.set(true)
  version.set(pinnedNode)
  pnpmVersion.set(pinnedPnpm)
  nodeProjectDir.set(frontendDir)
  // Repositories are centralized in settings.gradle.kts, including the Node distribution repository.
  distBaseUrl.unset()
  enableTaskRules.set(false)
}

// Plugin 7.1 captures Project in its lazy archive resolver. Store the resolved file, not that closure.
// Register after the plugin's own afterEvaluate hook, which supplies the archive provider.
afterEvaluate {
  tasks.withType<NodeSetupTask>().configureEach {
    nodeArchiveFile.finalizeValue()
  }
}

val frontendInstall = tasks.register<PnpmTask>("frontendInstall") {
  pnpmCommand.set(listOf("install", "--frozen-lockfile"))
  inputs.files(frontendDir.file("package.json"), frontendDir.file("pnpm-lock.yaml"),
      frontendDir.file("pnpm-workspace.yaml"), frontendDir.file(".npmrc"), frontendDir.file(".node-version"))
  inputs.files(fileTree(frontendDir) {
    include("apps/*/package.json", "libs/*/package.json")
  })
  outputs.dir(frontendDir.dir("node_modules"))
}
val frontendUiBuild = tasks.register<PnpmTask>("frontendUiBuild") {
  dependsOn(frontendInstall)
  pnpmCommand.set(listOf("exec", "ng", "build", "ui"))
  inputs.files(fileTree(frontendDir.dir("libs/ui")) { exclude("dist/**") },
      frontendDir.file("angular.json"), frontendDir.file("tsconfig.json"))
  outputs.dir(frontendDir.dir("libs/ui/dist"))
}
val frontendCatalogueBuild = tasks.register<PnpmTask>("frontendCatalogueBuild") {
  dependsOn(frontendUiBuild)
  pnpmCommand.set(listOf("exec", "ng", "build", "catalogue"))
  inputs.files(fileTree(frontendDir.dir("libs/ui")), frontendDir.file("angular.json"),
      frontendDir.file("tsconfig.json"))
  outputs.dir(frontendDir.dir("dist/catalogue"))
}
val frontendFormatCheck = tasks.register<PnpmTask>("frontendFormatCheck") {
  dependsOn(frontendInstall)
  pnpmCommand.set(listOf("run", "format:check"))
}
tasks.register<PnpmTask>("frontendFormat") {
  dependsOn(frontendInstall)
  pnpmCommand.set(listOf("run", "format"))
}
val frontendCheck = tasks.register<PnpmTask>("frontendCheck") {
  dependsOn(frontendCatalogueBuild, frontendFormatCheck)
  pnpmCommand.set(listOf("exec", "node", "scripts/check-tokens.mjs"))
}
val frontendBrowserImage = tasks.register<BrowserImageTask>("frontendBrowserImage") {
  description = "Builds the pinned Playwright test environment, including browsers and OS libraries."
  workspaceDirectory.set(frontendDir)
  nodeVersion.set(pinnedNode)
  pnpmVersion.set(pinnedPnpm)
  playwrightVersion.set(frontendPackage.map { text ->
    Regex("\"@playwright/test\"\\s*:\\s*\"([0-9]+\\.[0-9]+\\.[0-9]+)\"")
        .find(text)?.groupValues?.get(1)
        ?: error("frontend/package.json must pin @playwright/test exactly")
  })
  sources.from(fileTree(frontendDir) {
    exclude("**/node_modules/**", "**/dist/**", "**/.gradle/**", "**/build/**",
        ".angular/**", "out-tsc/**", "test-results/**", "playwright-report/**",
        "tests/baselines/**")
  })
  imageId.set(layout.buildDirectory.file("playwright/image-id"))
}
tasks.register<PnpmTask>("frontendCatalogueServe") {
  dependsOn(frontendUiBuild)
  pnpmCommand.set(listOf("exec", "ng", "serve", "catalogue", "--port", "4201"))
}
tasks.named("assemble") { dependsOn(frontendCatalogueBuild) }
tasks.named("check") { dependsOn(frontendCheck) }

// Consumers resolve managed tools as artifacts, without reading another project's model.
configurations.consumable("frontendNode") {
  outgoing.artifact(node.resolvedNodeDir) { builtBy(frontendInstall) }
}
configurations.consumable("frontendPnpm") {
  outgoing.artifact(node.pnpmWorkDir.dir(pinnedPnpm.map { "pnpm-v$it" })) {
    builtBy(frontendInstall)
  }
}
configurations.consumable("frontendBrowserImage") {
  outgoing.artifact(frontendBrowserImage.flatMap { it.imageId }) { builtBy(frontendBrowserImage) }
}

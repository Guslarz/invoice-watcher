plugins {
  base
  alias(libs.plugins.axion.release)
}

// Included builds are not part of the task graph on their own: include their checks explicitly.
tasks.check {
  dependsOn(gradle.includedBuild("settings").task(":check"))
  dependsOn(gradle.includedBuild("conventions").task(":check"))
}

// The only cross-project access left in the build logic; with the plugins listed in
// docs/agents/backlog/isolated-projects.md it still blocks Isolated Projects.
allprojects {
  version = rootProject.scmVersion.version
}

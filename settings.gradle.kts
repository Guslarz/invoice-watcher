pluginManagement {
  includeBuild("gradle/build-logic/settings")
}

plugins {
  id("settings.module-discovery")
}

rootProject.name = "invoice-watcher"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

dependencyResolutionManagement {
  repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
  repositories {
    mavenCentral()
  }
}

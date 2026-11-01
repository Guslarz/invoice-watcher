rootProject.name = "conventions"

dependencyResolutionManagement {
  repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
  repositories {
    gradlePluginPortal()
  }
  versionCatalogs {
    create("libs") {
      from(files("../../libs.versions.toml"))
    }
  }
}

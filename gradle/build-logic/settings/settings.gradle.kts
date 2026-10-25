rootProject.name = "settings"

dependencyResolutionManagement {
  repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
  repositories {
    gradlePluginPortal()
    mavenCentral()
  }
  versionCatalogs {
    create("libs") {
      from(files("../../libs.versions.toml"))
    }
  }
}

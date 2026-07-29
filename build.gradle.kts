plugins {
  alias(libs.plugins.axion.release)
}

allprojects {
  group = "com.guslarz.invoice-watcher"
  version = rootProject.scmVersion.version
}

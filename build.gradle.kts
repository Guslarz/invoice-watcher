plugins {
  alias(libs.plugins.axion.release)
}

allprojects {
  group = "com.skaczmarek.geoportal"
  version = rootProject.scmVersion.version
}

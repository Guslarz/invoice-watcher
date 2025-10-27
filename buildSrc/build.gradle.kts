plugins {
  `kotlin-dsl`
}

java {
  sourceCompatibility = JavaVersion.VERSION_24
  targetCompatibility = JavaVersion.VERSION_24
}

repositories {
  gradlePluginPortal()
}

dependencies {
  implementation(projects.plugins)
  implementation(libs.gradle.docker.plugin)
  implementation(libs.lombok.plugin)
  implementation(libs.spotbugs.gradle.plugin)
  implementation(libs.spring.boot.plugin)
  implementation(libs.spring.dependency.management.plugin)
}

plugins {
  `kotlin-dsl`
}

java {
  sourceCompatibility = JavaVersion.VERSION_25
  targetCompatibility = JavaVersion.VERSION_25
}

repositories {
  gradlePluginPortal()
}

dependencies {
  implementation(libs.gradle.docker.plugin)
  implementation(libs.lombok.plugin)
  implementation(libs.spotbugs.gradle.plugin)
  implementation(libs.spring.boot.plugin)
  implementation(libs.spring.dependency.management.plugin)
}

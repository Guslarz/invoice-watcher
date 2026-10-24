plugins {
  `kotlin-dsl`
}

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(25)
  }
}

dependencies {
  implementation(libs.gradle.docker.plugin)
  implementation(libs.lombok.plugin)
  implementation(libs.spotbugs.gradle.plugin)
  implementation(libs.spring.boot.plugin)
  implementation(libs.spring.dependency.management.plugin)
}

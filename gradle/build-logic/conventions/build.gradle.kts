plugins {
  `kotlin-dsl`
}

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(25)
  }
}

dependencies {
  implementation(libs.gradle.node.plugin)
  implementation(libs.gradle.docker.plugin)
  implementation(libs.lombok.plugin)
  implementation(libs.spotbugs.gradle.plugin)
  implementation(libs.spring.boot.plugin)
  implementation(libs.spring.dependency.management.plugin)
}

testing {
  suites {
    named<JvmTestSuite>("test") {
      useJUnitJupiter()
      dependencies {
        implementation(platform(libs.spring.boot.dependencies))
        implementation(libs.assertj.core)
        implementation(libs.spring.boot)
        implementation(libs.spring.boot.configuration.processor)
      }
    }
  }
}

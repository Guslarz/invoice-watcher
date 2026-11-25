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

  // Align Jackson 2 brought in by the Node plugin.
  implementation(platform(libs.jackson2.bom))
  constraints {
    implementation(libs.build.commons.lang3) {
      because("the Spring Boot plugin's buildpack dependencies select a vulnerable version")
    }
  }
}

testing {
  suites {
    named<JvmTestSuite>("test") {
      useJUnitJupiter()
      dependencies {
        implementation(platform(libs.spring.boot.dependencies))
        implementation(libs.assertj.core)
      }
    }
  }
}

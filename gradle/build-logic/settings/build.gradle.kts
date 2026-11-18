plugins {
  `kotlin-dsl`
}

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(25)
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

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
        implementation(libs.assertj.core)
      }
    }
  }
}

package jvm

plugins {
  java
  id("jvm.code-analysis-conventions")
  id("docs.reference-conventions")
  id("io.freefair.lombok")
}

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(25)
  }
}

// @SuppressFBWarnings; the Lombok plugin adds it too, but only as a side effect.
dependencies {
  compileOnly(spotbugs.toolVersion.map { "com.github.spotbugs:spotbugs-annotations:$it" })
}

tasks.withType<Test>().configureEach {
  useJUnitPlatform()
}

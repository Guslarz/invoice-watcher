package jvm

plugins {
  java
  id("jvm.code-analysis-conventions")
  id("io.freefair.lombok")
}

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(25)
  }
}

tasks.withType<Test>().configureEach {
  useJUnitPlatform()
}

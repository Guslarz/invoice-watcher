package jvm

plugins {
  id("jvm.lib-conventions")
  `java-test-fixtures`
}

val libs = versionCatalogs.named("libs")
dependencies {
  testFixturesApi(platform(libs.findLibrary("spring-boot-dependencies").get()))
  testImplementation(libs.findLibrary("spring-boot-starter-test").get())
  testRuntimeOnly(libs.findLibrary("junit-platform-launcher").get())
}

package springboot

import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
  id("jvm.common-conventions")
  id("io.spring.dependency-management")
}

val libs = versionCatalogs.named("libs")

dependencyManagement {
  imports {
    mavenBom(libs.findLibrary("spring-cloud-gcp-dependencies").get().get().toString())
    // Last, so its versions win over the ones the GCP BOM manages too.
    mavenBom(SpringBootPlugin.BOM_COORDINATES)
  }
  dependencies {
    // Use patched versions for Google Cloud and Kafka transitive dependencies.
    dependency(libs.findLibrary("guava").get().get().toString())
    dependency(libs.findLibrary("lz4-java").get().get().toString())
    dependency(libs.findLibrary("snappy-java").get().get().toString())
  }
}

// The configuration processor reads property descriptions from Javadoc, which a type that was not
// recompiled no longer has: an incremental compile would write metadata without them.
val additionalMetadata = objects.newInstance(AdditionalMetadata::class.java)
additionalMetadata.files.from(sourceSets.main.map { source ->
  source.resources.matching { include("META-INF/additional-spring-configuration-metadata.json") }
})
tasks.named<JavaCompile>("compileJava") {
  options.isIncremental = false
  options.compilerArgumentProviders.add(additionalMetadata)
  doLast(SecretMetadata())
  doLast(ImplementationMetadata())
}

dependencies {
  annotationProcessor(libs.findLibrary("spring-boot-configuration-processor").get())

  testImplementation(libs.findLibrary("spring-boot-starter-test").get())
  // Gradle 9 no longer provides it; the Spring Boot plugin adds it only to applications.
  testRuntimeOnly(libs.findLibrary("junit-platform-launcher").get())
}

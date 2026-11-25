package jvm

import com.github.spotbugs.snom.SpotBugsTask

plugins {
  id("checkstyle")
  id("com.github.spotbugs")
}

val libs = versionCatalogs.named("libs")

checkstyle {
  toolVersion = libs.findVersion("checkstyle").get().requiredVersion
  maxWarnings = 0
}

dependencies {
  constraints {
    add("checkstyle", libs.findLibrary("guava").get()) {
      because("Checkstyle selects a vulnerable Guava version")
    }
    add("checkstyle", libs.findLibrary("plexus-utils").get()) {
      because("Checkstyle's Doxia dependencies select a vulnerable Plexus Utils version")
    }
  }
}

spotbugs {
  excludeFilter = isolated.rootProject.projectDirectory.file("config/spotbugs/exclude.xml").asFile
}

// Static analysis only, without tests; `check` (and so `build`) runs the same tasks.
tasks.register("analyze") {
  group = "verification"
  description = "Runs Checkstyle and SpotBugs."
  dependsOn(tasks.withType<Checkstyle>(), tasks.withType<SpotBugsTask>())
}

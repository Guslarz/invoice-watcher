package jvm

import com.github.spotbugs.snom.SpotBugsTask

plugins {
  id("checkstyle")
  id("com.github.spotbugs")
}

checkstyle {
  toolVersion = "11.0.0"
  maxWarnings = 0
}

spotbugs {
  excludeFilter = rootProject.file("config/spotbugs/exclude.xml")
}

// Static analysis only, without tests; `check` (and so `build`) runs the same tasks.
tasks.register("analyze") {
  group = "verification"
  description = "Runs Checkstyle and SpotBugs."
  dependsOn(tasks.withType<Checkstyle>(), tasks.withType<SpotBugsTask>())
}

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

tasks {
  val analyze = register("analyze") {
    dependsOn("check")
  }

  named("build") {
    dependsOn(analyze)
  }
}

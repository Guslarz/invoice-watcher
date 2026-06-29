plugins {
  `kotlin-dsl`
}

repositories {
  gradlePluginPortal()
}

java {
  sourceCompatibility = JavaVersion.VERSION_25
  targetCompatibility = JavaVersion.VERSION_25
}

gradlePlugin {
  plugins {
    create("docker-configuration") {
      id = "docker-configuration"
      implementationClass = "configurations.docker.DockerConfigurationPlugin"
    }
  }
}

plugins {
  `kotlin-dsl`
}

repositories {
  gradlePluginPortal()
}

java {
  sourceCompatibility = JavaVersion.VERSION_24
  targetCompatibility = JavaVersion.VERSION_24
}

gradlePlugin {
  plugins {
    create("docker-configuration") {
      id = "docker-configuration"
      implementationClass = "configurations.docker.DockerConfigurationPlugin"
    }
  }
}

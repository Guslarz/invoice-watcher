plugins {
  id("docker.image-conventions")
}

description = "Docker image of the registration backend application."

dependencies {
  dockerImageResource(projects.apps.userRegistrationBackendApp)
}

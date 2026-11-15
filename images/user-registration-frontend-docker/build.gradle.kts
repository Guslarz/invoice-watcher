plugins {
  id("docker.image-conventions")
}

description = "Docker image of the registration frontend application."

dependencies {
  dockerImageResource(projects.apps.userRegistrationFrontendApp)
}

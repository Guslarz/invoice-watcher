plugins {
  id("docker.image-conventions")
}

description = "Docker image of the mail producer application."

dependencies {
  dockerImageResource(projects.apps.mailProducerApp)
}

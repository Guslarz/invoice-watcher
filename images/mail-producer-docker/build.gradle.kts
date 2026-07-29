plugins {
  id("docker.image-conventions")
}

dependencies {
  dockerImageResource(projects.apps.mailProducerApp)
}

plugins {
  id("frontend.app-conventions")
}

description = "Angular registration frontend consuming the shared component and design-token library."

frontendApplication {
  angularProject.set("registration")
  outputPath.set("dist/registration")
}

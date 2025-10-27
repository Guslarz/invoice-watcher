import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.DockerSaveImage
import configurations.docker.DockerConfigurationExtension
import org.apache.tools.ant.filters.ReplaceTokens

plugins {
  id("com.bmuschko.docker-remote-api")
  id("docker-configuration")
}

val resourcesDir = layout.buildDirectory.dir("resources")
val image = "${name}:${version}"
val imageFile = layout.buildDirectory.file("${name}-${version}.tar.gz")

dockerConfiguration.configureProducer()

val imageBackendJar = configurations.create("imageBackendJar") {
}

val imageFrontendStatics = configurations.create("imageFrontendStatics") {
}

tasks {
  val copySources = register<Copy>("copySources") {
    from("src") {
      filter<ReplaceTokens>("tokens" to mapOf(
        "PROJECT_VERSION" to version
      ))
    }
    into(resourcesDir)
  }

  val copyBackendJar = register<Copy>("copyBackendJar") {
    dependsOn(imageBackendJar)
    if (imageBackendJar.dependencies.size > 1) {
      throw RuntimeException("Only one imageBackendJar allowed ${imageBackendJar.dependencies.size}")
    } else if (imageBackendJar.dependencies.size == 1) {
      val jarTask = (imageBackendJar.dependencies.first() as ProjectDependency).dependencyProject.tasks["bootJar"]
      dependsOn(jarTask)
      from(jarTask.outputs.files.singleFile)
      into(resourcesDir)
    }
  }

  val copyFrontendStatics = register<Copy>("copyFrontendStatics") {
    dependsOn(imageBackendJar)
    if (imageFrontendStatics.dependencies.size > 1) {
      throw RuntimeException("Only one imageFrontendStatics allowed ${imageBackendJar.dependencies.size}")
    } else if (imageFrontendStatics.dependencies.size == 1) {
      val buildTask = (imageFrontendStatics.dependencies.first() as ProjectDependency).dependencyProject.tasks["build"]
      dependsOn(buildTask)
      from(buildTask.outputs.files.singleFile)
      into(resourcesDir)
    }
  }

  val copySourceProject = register("copySourceProject") {
    dependsOn(copyBackendJar, copyFrontendStatics)
  }

  val buildImage = register<DockerBuildImage>("buildImage") {
    dependsOn(copySources, copySourceProject)
    inputDir.set(resourcesDir)
    images.add(image)
  }

  val saveImage = register<DockerSaveImage>("saveImage") {
    dependsOn(buildImage)
    images.add(image)
    destFile.set(imageFile)
  }

  register("build") {
    dependsOn(saveImage)
    outputs.file(imageFile)
  }
}

artifacts {
  add(DockerConfigurationExtension.ProducerName, imageFile) {
    builtBy("build")
  }
}

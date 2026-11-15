package frontend

import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.process.ExecOperations

abstract class BrowserImageTask : DefaultTask() {
  @get:InputFiles abstract val sources: ConfigurableFileCollection
  @get:Internal abstract val workspaceDirectory: DirectoryProperty
  @get:Input abstract val nodeVersion: Property<String>
  @get:Input abstract val pnpmVersion: Property<String>
  @get:Input abstract val playwrightVersion: Property<String>
  @get:OutputFile abstract val imageId: RegularFileProperty
  @get:Inject abstract val execOperations: ExecOperations

  init {
    // Docker's layer cache handles reuse; the daemon may have removed a previously built image.
    outputs.upToDateWhen { false }
  }

  @TaskAction
  fun buildImage() {
    val workspace = workspaceDirectory.get().asFile
    val output = imageId.get().asFile
    output.parentFile.mkdirs()
    execOperations.exec {
      commandLine("docker", "build", "--build-arg", "NODE_VERSION=${nodeVersion.get()}",
          "--build-arg", "PNPM_VERSION=${pnpmVersion.get()}",
          "--build-arg", "PLAYWRIGHT_VERSION=${playwrightVersion.get()}",
          "--iidfile", output.absolutePath, "--file", workspace.resolve("playwright.Dockerfile"),
          workspace.absolutePath)
    }
  }
}

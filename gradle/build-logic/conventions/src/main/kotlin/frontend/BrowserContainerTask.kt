package frontend

import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.process.ExecOperations

abstract class BrowserContainerTask : DefaultTask() {
  @get:InputFiles abstract val image: ConfigurableFileCollection
  @get:Internal abstract val workspaceDirectory: DirectoryProperty
  @get:Internal abstract val reportsDirectory: DirectoryProperty
  @get:Input abstract val playwrightConfig: Property<String>
  @get:Input abstract val updateSnapshots: Property<Boolean>
  @get:Inject abstract val execOperations: ExecOperations

  init { updateSnapshots.convention(false) }

  @TaskAction
  fun runBrowserTests() {
    val reports = reportsDirectory.get().asFile
    reports.mkdirs()
    val cache = reports.resolve("angular-cache").apply { mkdirs() }
    val baselines = workspaceDirectory.get().dir("tests/baselines").asFile
    val name = "iw-playwright-${UUID.randomUUID()}"
    val command = mutableListOf("docker", "run", "--rm", "--init", "--name", name,
        "--shm-size=1g", "--user", "${hostId("-u")}:${hostId("-g")}", "--env", "HOME=/tmp",
        "--mount", "type=bind,source=${cache.absolutePath},target=/workspace/.angular",
        "--mount", "type=bind,source=${reports.absolutePath},target=/reports",
        "--mount", "type=bind,source=${baselines.absolutePath},target=/workspace/tests/baselines" +
            if (updateSnapshots.get()) "" else ",readonly",
        image.singleFile.readText().trim(), "pnpm", "exec", "playwright", "test",
        "--config", playwrightConfig.get(), "--output", "/reports/test-results")
    if (updateSnapshots.get()) command.add("--update-snapshots")
    try {
      execOperations.exec { commandLine(command) }
    } finally {
      // Clean up our own container after failed tests or an interrupted Docker client.
      execOperations.exec {
        commandLine("docker", "rm", "--force", name)
        isIgnoreExitValue = true
        standardOutput = ByteArrayOutputStream()
        errorOutput = ByteArrayOutputStream()
      }
    }
  }

  private fun hostId(flag: String): String {
    val output = ByteArrayOutputStream()
    execOperations.exec { commandLine("id", flag); standardOutput = output }
    return output.toString().trim()
  }
}

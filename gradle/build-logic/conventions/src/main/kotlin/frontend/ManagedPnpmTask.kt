package frontend

import java.io.File
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

abstract class ManagedPnpmTask : DefaultTask() {
  @get:InputFiles abstract val nodeRuntime: ConfigurableFileCollection
  @get:InputFiles abstract val pnpmRuntime: ConfigurableFileCollection
  @get:Internal abstract val workspaceDirectory: DirectoryProperty
  @get:Input abstract val arguments: ListProperty<String>
  @get:Input abstract val extraEnvironment: MapProperty<String, String>
  @get:Inject abstract val execOperations: ExecOperations

  init { extraEnvironment.convention(emptyMap()) }

  @TaskAction
  fun runPnpm() {
    val nodeBin = nodeRuntime.singleFile.resolve("bin")
    val pnpmBin = pnpmRuntime.singleFile.resolve("bin")
    execOperations.exec {
      workingDir(workspaceDirectory.get().asFile)
      commandLine(pnpmBin.resolve("pnpm"), *arguments.get().toTypedArray())
      environment("PATH", "$nodeBin${File.pathSeparator}$pnpmBin${File.pathSeparator}${System.getenv("PATH")}")
      environment(extraEnvironment.get())
    }
  }
}

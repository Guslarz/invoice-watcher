import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

rootProject.name = "invoice-watcher"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

registerModules("apps", "libs", "images")

fun registerModules(vararg modules: String) {
  modules.forEach{ internalRegisterModules(arrayOf(it)) }
}

private fun internalRegisterModules(path: Array<String>) {
  Path(settings.rootProject.projectDir.path).resolve(path.joinToString("/")).listDirectoryEntries().forEach {
    val newPath = path + it.name
    if (it.resolve("build.gradle.kts").exists()) {
      registerModule(newPath)
    } else {
      internalRegisterModules(newPath)
    }
  }
}

private fun registerModule(path: Array<String>) {
  val modulePath = ":${path[0]}:${path.slice(1 until path.size).joinToString("-")}"
  val moduleDir = path.joinToString("/")
  settings.include(modulePath)
  settings.project(modulePath).projectDir = settings.rootDir.resolve(moduleDir)
}

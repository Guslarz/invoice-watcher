package settings

import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

// Every directory under apps/, images/ and libs/ containing a build.gradle.kts becomes a project:
// libs/mail-producer/dao/api -> :libs:mail-producer-dao-api.
registerModules("apps", "images", "libs")

fun registerModules(vararg modules: String) {
  modules.forEach{ internalRegisterModules(arrayOf(it)) }
}

fun internalRegisterModules(path: Array<String>) {
  Path(settings.rootProject.projectDir.path).resolve(path.joinToString("/")).listDirectoryEntries().filter { it.isDirectory() }.forEach {
    val newPath = path + it.name
    if (it.resolve("build.gradle.kts").exists()) {
      registerModule(newPath)
    } else {
      internalRegisterModules(newPath)
    }
  }
}

fun registerModule(path: Array<String>) {
  val modulePath = ":${path[0]}:${path.slice(1 until path.size).joinToString("-")}"
  val moduleDir = path.joinToString("/")
  settings.findProject(modulePath)?.let {
    throw GradleException("Module path $modulePath of $moduleDir already used by ${it.projectDir}")
  }
  settings.include(modulePath)
  settings.project(modulePath).projectDir = settings.rootDir.resolve(moduleDir)
}

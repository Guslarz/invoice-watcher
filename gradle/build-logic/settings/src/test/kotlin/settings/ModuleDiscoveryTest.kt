package settings

import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ModuleDiscoveryTest {

  @TempDir
  lateinit var projectDir: File

  private lateinit var uut: GradleRunner

  @BeforeEach
  fun setUp() {
    projectDir.resolve("settings.gradle.kts").writeText(
      """
      plugins {
        id("settings.module-discovery")
      }
      """.trimIndent()
    )
    // Prints every registered project with its directory, so tests can assert both.
    projectDir.resolve("build.gradle.kts").writeText(
      """
      subprojects {
        println("module ${'$'}path -> ${'$'}{projectDir.relativeTo(rootDir).invariantSeparatorsPath}")
      }
      """.trimIndent()
    )
    listOf("apps", "images", "libs").forEach { projectDir.resolve(it).mkdirs() }
    uut = GradleRunner.create()
      .withProjectDir(projectDir)
      .withPluginClasspath()
      .withArguments("help")
  }

  @Test
  fun registersModuleUnderNestedDirectoriesWithDashJoinedPath() {
    // given
    module("libs/mail-producer/dao/api")
    module("apps/mail-producer-app")

    // when
    val result = uut.build()

    // then
    assertThat(result.output)
      .contains("module :libs:mail-producer-dao-api -> libs/mail-producer/dao/api")
      .contains("module :apps:mail-producer-app -> apps/mail-producer-app")
  }

  @Test
  fun doesNotDescendIntoModule() {
    // given
    module("libs/mail-producer")
    module("libs/mail-producer/api")

    // when
    val result = uut.build()

    // then
    assertThat(result.output)
      .contains("module :libs:mail-producer -> libs/mail-producer")
      .doesNotContain("mail-producer-api")
  }

  @Test
  fun ignoresPlainFilesAndDirectoriesWithoutModules() {
    // given
    projectDir.resolve("apps/README.md").writeText("not a module")
    projectDir.resolve("libs/empty/nested").mkdirs()
    module("libs/area/api")

    // when
    val result = uut.build()

    // then
    assertThat(result.output)
      .contains("module :libs:area-api -> libs/area/api")
      .doesNotContain("README")
      .doesNotContain(":libs:empty")
  }

  @Test
  fun failsWhenTwoDirectoriesMapToTheSameModulePath() {
    // given
    module("libs/a-b/api")
    module("libs/a/b-api")

    // when
    val result = uut.buildAndFail()

    // then
    assertThat(result.output)
      .contains("Module path :libs:a-b-api of libs/")
      .contains("already used by")
  }

  private fun module(path: String) {
    projectDir.resolve(path).mkdirs()
    projectDir.resolve("$path/build.gradle.kts").writeText("")
  }
}

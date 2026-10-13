plugins {
  id("spring-boot-app-conventions")
}

description = "Command-line tool that generates AES keys and encrypts and decrypts secrets."

dependencies {
  implementation(projects.libs.utilAes)

  implementation(libs.picocli)
}

// Stable name, so the tool can be run as `java -jar apps/secret-helper-app/build/libs/secret-helper.jar`
tasks.bootJar {
  archiveFileName = "secret-helper.jar"
}

// Usage texts for the generated documentation reference (scripts/gen-reference.py).
// Every command printed under "Commands:" by `secret-helper --help` must be listed here.
val cliCommands = listOf("", "generate-key", "encrypt", "decrypt")

val cliReference = tasks.register("cliReference") {
  group = "documentation"
  description = "Writes the --help output of every secret-helper command."
}

cliCommands.forEach { command ->
  val name = command.ifEmpty { "secret-helper" }
  val task = tasks.register<JavaExec>("cliReference-$name") {
    group = "documentation"
    description = "Writes the --help output of $name."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "com.guslarz.invoicewatcher.secret.helper.app.SecretHelperApp"
    args = listOf(command, "--help").filter { it.isNotEmpty() }
    val output = rootProject.layout.buildDirectory.file("docs-reference/cli/$name.txt")
    outputs.file(output)
    doFirst {
      standardOutput = output.get().asFile.outputStream()
    }
  }
  cliReference.configure { dependsOn(task) }
}

package springboot

import groovy.json.JsonSlurper
import java.io.File
import java.io.StringWriter
import java.nio.file.Path
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.ToolProvider
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.configurationprocessor.ConfigurationMetadataAnnotationProcessor

class SecretMetadataTest {
  @TempDir
  lateinit var directory: Path

  @Test
  fun sourceScalarDeclarationKeepsSourceAndCompiledMetadataConsistent() {
    // given
    val metadata = directory.resolve("resources/META-INF/additional-spring-configuration-metadata.json").toFile()
    metadata.parentFile.mkdirs()
    metadata.writeText("""
      {"properties": [{"name": "fixture.client-secret", "type": "java.lang.String"}]}
    """.trimIndent())

    // when
    val generated = compileProperties(listOf(metadata))
    val original = JsonSlurper().parse(generated) as Map<*, *>
    val originalProperties = original["properties"] as List<*>
    val originalSecret = originalProperties.map { it as Map<*, *> }.single { it["name"] == "fixture.client-secret" }
    // Spring preserves the Java type even when additional metadata declares a scalar.
    assertThat(originalSecret["type"]).isEqualTo("com.guslarz.invoicewatcher.secret.api.Secret")
    rewriteSecretMetadata(generated, listOf(metadata))

    // then
    val properties = (JsonSlurper().parse(generated) as Map<*, *>)["properties"] as List<*>
    val secret = properties.map { it as Map<*, *> }.single { it["name"] == "fixture.client-secret" }
    assertThat(secret["type"]).isEqualTo("java.lang.String")
    assertThat(secret["description"]).isEqualTo("Required. Encrypted client secret.")
    assertThat(secret["sourceType"]).isEqualTo("fixture.Properties")
    val clientId = properties.map { it as Map<*, *> }.single { it["name"] == "fixture.client-id" }
    assertThat(clientId["type"]).isEqualTo("java.lang.String")
    val originalClientId = originalProperties.map { it as Map<*, *> }.single { it["name"] == "fixture.client-id" }
    assertThat(clientId).isEqualTo(originalClientId)
    val normalized = generated.readText()
    rewriteSecretMetadata(generated, listOf(metadata))
    assertThat(generated.readText()).isEqualTo(normalized)
  }

  @Test
  fun missingSourceDeclarationFailsWithThePropertyNameAndRemedy() {
    // given
    val generated = compileProperties(emptyList())
    val original = generated.readText()

    // when / then
    assertThatIllegalArgumentException().isThrownBy { rewriteSecretMetadata(generated) }
      .withMessageContaining("fixture.client-secret")
      .withMessageContaining("java.lang.String")
      .withMessageContaining("META-INF/additional-spring-configuration-metadata.json")
    assertThat(generated.readText()).isEqualTo(original)
  }

  @Test
  fun modulesWithoutSecretPropertiesOrMetadataNeedNoOverride() {
    // given
    val metadata = directory.resolve("spring-configuration-metadata.json").toFile()
    val original = """{"properties":[{"name":"fixture.port","type":"java.lang.Integer"}]}"""
    metadata.writeText(original)

    // when
    rewriteSecretMetadata(metadata)
    rewriteSecretMetadata(directory.resolve("missing.json").toFile())

    // then
    assertThat(metadata.readText()).isEqualTo(original)
  }

  private fun compileProperties(additionalMetadata: List<File>): File {
    val sources = directory.resolve("sources").toFile().apply { mkdirs() }
    val secret = sources.resolve("Secret.java").apply {
      writeText("""
        package com.guslarz.invoicewatcher.secret.api;
        public interface Secret { String getEncrypted(); String getDecrypted(); }
      """.trimIndent())
    }
    val properties = sources.resolve("Properties.java").apply {
      writeText("""
        package fixture;
        import com.guslarz.invoicewatcher.secret.api.Secret;
        import org.springframework.boot.context.properties.ConfigurationProperties;
        /**
         * Fixture settings.
         * @param clientId Required. Client identifier.
         * @param clientSecret Required. Encrypted client secret.
         */
        @ConfigurationProperties("fixture")
        public record Properties(String clientId, Secret clientSecret) {}
      """.trimIndent())
    }
    val output = directory.resolve("classes").toFile().apply { mkdirs() }
    val compiler = ToolProvider.getSystemJavaCompiler()
    val diagnostics = DiagnosticCollector<JavaFileObject>()
    val log = StringWriter()
    val boot = File(ConfigurationProperties::class.java.protectionDomain.codeSource.location.toURI())
    compiler.getStandardFileManager(diagnostics, null, null).use { files ->
      val options = listOf("-parameters", "-proc:only", "-classpath", boot.path, "-d", output.path) +
        additionalMetadataArguments(additionalMetadata)
      val uut = compiler.getTask(log, files, diagnostics, options, null,
        files.getJavaFileObjects(secret, properties))
      uut.setProcessors(listOf(ConfigurationMetadataAnnotationProcessor()))
      assertThat(uut.call()).withFailMessage("%s%n%s", log, diagnostics.diagnostics).isTrue()
    }
    return output.resolve("META-INF/spring-configuration-metadata.json")
  }
}

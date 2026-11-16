package springboot

import groovy.json.JsonSlurper
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class SecretMetadataTest {
  @TempDir
  lateinit var directory: Path

  @Test
  fun describesSecretInputAsStringAndPreservesOtherMetadata() {
    // given
    val file = directory.resolve("spring-configuration-metadata.json").toFile()
    file.writeText("""
      {
        "groups": [{"name": "fixture", "type": "fixture.Properties"}],
        "properties": [
          {"name": "fixture.client-secret", "type": "com.guslarz.invoicewatcher.secret.api.Secret",
           "description": "Required. Encrypted client secret.", "sourceType": "fixture.Properties"},
          {"name": "fixture.client-id", "type": "java.lang.String", "defaultValue": "fixture"},
          {"name": "fixture.retries", "type": "java.lang.Integer", "defaultValue": 3}
        ],
        "hints": [{"name": "fixture.client-id", "providers": [{"name": "any"}]}]
      }
    """.trimIndent())
    val original = JsonSlurper().parse(file) as Map<*, *>

    // when
    rewriteSecretMetadata(file)

    // then
    val result = JsonSlurper().parse(file) as Map<*, *>
    val properties = result["properties"] as List<*>
    val secret = properties[0] as Map<*, *>
    assertThat(secret["type"]).isEqualTo("java.lang.String")
    assertThat(secret["description"]).isEqualTo("Required. Encrypted client secret.")
    assertThat(secret["sourceType"]).isEqualTo("fixture.Properties")
    assertThat(properties.drop(1)).isEqualTo((original["properties"] as List<*>).drop(1))
    assertThat(result["groups"]).isEqualTo(original["groups"])
    assertThat(result["hints"]).isEqualTo(original["hints"])
    val normalized = file.readText()
    rewriteSecretMetadata(file)
    assertThat(file.readText()).isEqualTo(normalized)
  }

  @Test
  fun leavesModulesWithoutSecretPropertiesUntouched() {
    // given
    val file = directory.resolve("spring-configuration-metadata.json").toFile()
    val original = """{"properties":[{"name":"fixture.port","type":"java.lang.Integer"}]}"""
    file.writeText(original)

    // when
    rewriteSecretMetadata(file)

    // then
    assertThat(file.readText()).isEqualTo(original)
  }

  @Test
  fun skipsModulesWithoutConfigurationMetadata() {
    // given
    val file = directory.resolve("absent.json").toFile()

    // when
    rewriteSecretMetadata(file)

    // then
    assertThat(file).doesNotExist()
  }
}

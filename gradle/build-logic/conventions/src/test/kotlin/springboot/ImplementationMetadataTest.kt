package springboot

import groovy.json.JsonSlurper
import java.nio.file.Path
import javax.tools.ToolProvider
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ImplementationMetadataTest {
  @TempDir lateinit var directory: Path

  @Test
  fun mergesImplementationHintsWithoutAnApiEnumAndDeduplicatesValues() {
    // given
    val api = readMetadata("""{"groups":[{"name":"invoice-watcher.example"}],"properties":[{"name":"invoice-watcher.example.mode","type":"java.lang.String"}]}""")
    val first = readMetadata("""{"hints":[{"name":"invoice-watcher.example.mode","values":[{"value":"first"}]}]}""")
    val future = readMetadata("""{"hints":[{"name":"invoice-watcher.example.mode","values":[{"value":"future"}]}]}""")
    // when
    val uut = mergeMetadata(listOf(api, first, future, first))
    // then
    assertThat(uut["properties"]).isEqualTo(api["properties"])
    val hints = uut["hints"] as List<*>
    assertThat((hints.single() as Map<*, *>)["values"])
      .isEqualTo(listOf(mapOf("value" to "first"), mapOf("value" to "future")))
    assertThat(mergeMetadata(listOf(future, first, api))).isEqualTo(uut)
    assertThat(mergeMetadata(listOf(api, first))).isNotEqualTo(uut)
  }

  @Test
  fun rejectsConflictingPropertyTypesInsteadOfChoosingByClasspathOrder() {
    // given
    val first = readMetadata("""{"properties":[{"name":"invoice-watcher.example.mode","type":"java.lang.String"}]}""")
    val second = readMetadata("""{"properties":[{"name":"invoice-watcher.example.mode","type":"java.lang.Boolean"}]}""")
    // when
    val action: () -> Unit = { mergeMetadata(listOf(first, second)); Unit }
    // then
    assertThatThrownBy(action).hasMessageContaining("invoice-watcher.example.mode").hasMessageContaining("type")
  }

  @Test
  fun derivesHintFromACompiledFutureBackendConditionWithoutInstantiatingIt() {
    // given
    val annotation = directory.resolve("ConditionalOnProperty.java").toFile()
    annotation.writeText("""
      package org.springframework.boot.autoconfigure.condition;
      @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
      @java.lang.annotation.Repeatable(org.springframework.boot.autoconfigure.condition.ConditionalOnProperties.class)
      public @interface ConditionalOnProperty {
        String prefix(); String[] name() default {}; String[] value() default {}; String havingValue();
      }
    """.trimIndent())
    val repeated = directory.resolve("ConditionalOnProperties.java").toFile()
    repeated.writeText("""
      package org.springframework.boot.autoconfigure.condition;
      @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
      public @interface ConditionalOnProperties { ConditionalOnProperty[] value(); }
    """.trimIndent())
    val backend = directory.resolve("FutureAutoConfiguration.java").toFile()
    backend.writeText("""
      package fixture;
      @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        prefix="invoice-watcher.example", name="mode", havingValue="future")
      @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        prefix="invoice-watcher.example", name="enabled", havingValue="true")
      public class FutureAutoConfiguration {
        static { if (true) throw new AssertionError("metadata must not initialize the backend"); }
      }
    """.trimIndent())
    val classes = directory.resolve("classes").toFile().apply { mkdirs() }
    assertThat(ToolProvider.getSystemJavaCompiler().run(null, null, null,
      "-d", classes.path, annotation.path, repeated.path, backend.path)).isZero()
    // when
    implementationMetadata(classes, emptyList())
    // then
    val uut = JsonSlurper().parse(classes.resolve("META-INF/spring-configuration-metadata.json")) as Map<*, *>
    assertThat(uut["hints"]).isEqualTo(listOf(mapOf("name" to "invoice-watcher.example.mode",
      "values" to listOf(mapOf("value" to "future")))))
  }
}

---
id: ADR-0028
status: accepted
date: 2026-11-24
---

# ADR-0028: Describe Secret scalars in source and packaged metadata

## Context

IntelliJ's Spring resolver infers project-source custom types as nested configuration and reads
generated configuration metadata from library scope. Rewriting compiled `Secret` metadata fixes
packaged consumers but leaves the project's YAML key unresolved. Source-resource additional
metadata makes the scalar key visible to IntelliJ. Spring's configuration processor still
preserves the Java type for an existing property, so that declaration does not replace the
compiled metadata adjustment. In Spring Boot 4.1.2,
[additional metadata merging](https://github.com/spring-projects/spring-boot/blob/v4.1.2/configuration-metadata/spring-boot-configuration-processor/src/main/java/org/springframework/boot/configurationprocessor/metadata/ConfigurationMetadata.java#L127)
updates an existing property's description, default value and deprecation information, with no
type override. Ignoring a property removes it after merging, including any manual declaration
under the same name, so ignore-and-redeclare cannot replace its type either.

## Decision

- Declare the external string type of each `Secret` configuration property in the owning
  module's source `META-INF/additional-spring-configuration-metadata.json`. Include only the
  necessary scalar declaration; keep descriptions and defaults owned by the Java declaration.
- Track additional metadata files as compiler inputs and pass their resource roots to Spring's
  processor through the shared convention. Keep the compiled metadata normalization, requiring
  a matching source string declaration before changing a `Secret` property's metadata type.
  Preserve Java types, runtime conversion and encrypted input formats.
- Test compiler output with the real annotation processor. Test IntelliJ separately with an
  isolated project containing source modules, placeholder YAML and a deliberately invalid key,
  comparing missing metadata, source metadata and linked YAML. A successful build alone does
  not establish IDE resolution.

## Consequences

Project sources and packaged libraries describe the same scalar input. Adding a `Secret`
configuration property requires a source metadata entry; the build fails with the property name
when it is missing. IDE regression checks need an installed IntelliJ with Spring support and
cached dependencies; normal build checks require neither IDE access nor local credentials.

# Code conventions

- Java 25, Google Java style (2-space indent) enforced by Checkstyle (`config/checkstyle`);
  SpotBugs exclusions in `config/spotbugs/exclude.xml` — add an exclusion only with a comment why.
- Lombok is applied to every module (`jvm.common-conventions`). Prefer records +
  `@Builder` for data, `@RequiredArgsConstructor` for injection, `@Slf4j` for logging,
  `@UtilityClass` for constant holders.
- Nullness annotations come from jspecify (`org.jspecify.annotations`), declared as
  `implementation(libs.jspecify)` in every module that uses them; not JSR-305 `javax.annotation`.
- Constants use `UPPER_SNAKE_CASE`, following
  [Google Java style](https://google.github.io/styleguide/javaguide.html#s5.2.4-constant-names):
  `static final` fields must be deeply immutable with no detectable method side effects.
  Non-constant fields use `lowerCamelCase`, even when `static final` (for example, random
  generators, streams and non-empty arrays).
- Checkstyle `ConstantName` checks static final names; `StaticVariableName` checks non-final
  static names. Checkstyle cannot determine deep immutability: suppress `ConstantName` only on
  an individual non-constant static final field, with a comment explaining why, and review its
  `lowerCamelCase` name. Enum constants use `UPPER_SNAKE_CASE` too.
- Logging: `log.info("> Start ...")` / `log.info("< Finished ...")` brackets around units of work;
  never log credentials or decrypted secrets.
- Secrets (passwords, tokens) are held as `Secret` from `libs/secret/api`, never as plain `String`
  outside the moment of use. Their values never go into the repository (ADR-0006).

## Tests

- JUnit 5 (+ `@ParameterizedTest` with `@MethodSource` for tables) and AssertJ.
- Method names describe the behavior in third person, without a `should` or `test` prefix:
  `savesPositionOnceWhenAllEventsConfirmed`.
- Body structured with `// given`, `// when`, `// then`; the object under test is named `uut`.
  `when` and `then` stay separate — capture an expected exception with `catchThrowable` and assert
  on it under `// then`.
- Tests that verify a spec clause name it: `@DisplayName("MAIL-004: ...")`.
- Pure unit tests by default; Spring context tests only for auto-configuration wiring.

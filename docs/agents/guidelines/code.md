# Code conventions

- Java 25, Google Java style (2-space indent) enforced by Checkstyle (`config/checkstyle`);
  SpotBugs exclusions in `config/spotbugs/exclude.xml` — add an exclusion only with a comment why.
- Lombok is applied to every module (`dependencies.lombok-conventions`). Prefer records +
  `@Builder` for data, `@RequiredArgsConstructor` for injection, `@Slf4j` for logging,
  `@UtilityClass` for constant holders.
- Logging: `log.info("> Start ...")` / `log.info("< Finished ...")` brackets around units of work;
  never log credentials or decrypted secrets.
- Secrets (passwords, tokens) are held as `Secret` from `libs/secret/api`, never as plain `String`
  outside the moment of use.

## Tests

- JUnit 5 (+ `@ParameterizedTest` with `@MethodSource` for tables) and AssertJ.
- Body structured with `// given`, `// when`, `// then`; the object under test is named `uut`.
- Tests that verify a spec clause name it: `@DisplayName("MAIL-004: ...")`.
- Pure unit tests by default; Spring context tests only for auto-configuration wiring.

package com.guslarz.invoicewatcher.secret.helper.app;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.security.InvalidKeyException;
import java.util.Arrays;
import java.util.NoSuchElementException;
import javax.crypto.AEADBadTagException;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

@Component
@RequiredArgsConstructor
public class SecretHelperRunner implements CommandLineRunner {

  private static final String SpringArgumentPrefix = "--spring.";

  @Override
  @SuppressFBWarnings("DM_EXIT")
  public void run(String @NonNull ... args) {
    System.exit(execute(args));
  }

  /**
   * Runs the command line without the arguments Spring Boot consumes and returns the exit status.
   */
  static int execute(String... args) {
    String[] cliArgs = Arrays.stream(args)
        .filter(arg -> !arg.startsWith(SpringArgumentPrefix))
        .toArray(String[]::new);
    return createCommandLine().execute(cliArgs);
  }

  static CommandLine createCommandLine() {
    return new CommandLine(new SecretHelperCommand())
        .setExecutionExceptionHandler((exception, commandLine, parseResult) -> {
          commandLine.getErr().println("Error: " + describe(exception));
          commandLine.getErr().flush();
          return 1;
        });
  }

  /**
   * Fixed message per failure; exception messages are not shown, as they may contain input.
   */
  private static String describe(Exception exception) {
    return switch (exception) {
      case AEADBadTagException ignored -> "decryption failed: wrong key or tampered value";
      case InvalidKeyException ignored -> "invalid key: expected a Base64-encoded 256-bit AES key";
      case NoSuchElementException ignored -> "input ended before all values were read";
      case IllegalArgumentException ignored ->
          "invalid key or encrypted value: not Base64 or too short";
      default -> "unexpected " + exception.getClass().getSimpleName();
    };
  }
}

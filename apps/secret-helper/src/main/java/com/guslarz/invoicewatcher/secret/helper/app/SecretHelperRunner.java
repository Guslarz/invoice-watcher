package com.guslarz.invoicewatcher.secret.helper.app;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

@Component
@RequiredArgsConstructor
public class SecretHelperRunner implements CommandLineRunner {

  @Override
  @SuppressFBWarnings("DM_EXIT")
  public void run(String @NonNull ... args) {
    CommandLine cli = new CommandLine(new SecretHelperCommand());
    int exitCode = cli.execute(args);
    System.exit(exitCode);
  }
}

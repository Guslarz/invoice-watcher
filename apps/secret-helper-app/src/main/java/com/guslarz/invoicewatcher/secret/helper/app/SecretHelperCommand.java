package com.guslarz.invoicewatcher.secret.helper.app;

import com.guslarz.invoicewatcher.secret.helper.app.subcommand.DecryptCommand;
import com.guslarz.invoicewatcher.secret.helper.app.subcommand.EncryptCommand;
import com.guslarz.invoicewatcher.secret.helper.app.subcommand.GenerateKeyCommand;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

@Command(
    name = "secret-helper",
    description = "Generates the AES key for InvoiceWatcher and encrypts and decrypts secrets "
        + "with it. All values are read interactively from standard input.",
    mixinStandardHelpOptions = true,
    versionProvider = ManifestVersionProvider.class,
    subcommands = {
        DecryptCommand.class,
        EncryptCommand.class,
        GenerateKeyCommand.class
    }
)
public class SecretHelperCommand implements Callable<Integer> {

  @Override
  public Integer call() {
    return 1;
  }
}

package com.guslarz.invoicewatcher.secret.helper.app.subcommand;

import com.guslarz.invoicewatcher.util.aes.AesUtil;
import picocli.CommandLine.Command;

import javax.crypto.SecretKey;
import java.util.concurrent.Callable;

@Command(
    name = "generate-key",
    description = "Prints a new random Base64-encoded AES-256 key.",
    mixinStandardHelpOptions = true
)
public class GenerateKeyCommand implements Callable<Integer> {

  private static final int KEY_LENGTH = 32;

  @Override
  public Integer call() {
    IO.println(AesUtil.encodeSecretKey(generateSecretKey()));
    return 0;
  }

  private SecretKey generateSecretKey() {
    byte[] key = new byte[KEY_LENGTH];
    AesUtil.fillRandomBytes(key);
    return AesUtil.toSecretKey(key);
  }
}

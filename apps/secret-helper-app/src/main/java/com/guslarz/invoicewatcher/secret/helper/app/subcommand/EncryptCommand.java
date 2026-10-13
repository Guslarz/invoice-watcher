package com.guslarz.invoicewatcher.secret.helper.app.subcommand;

import com.guslarz.invoicewatcher.secret.helper.app.util.CommandUtil;
import com.guslarz.invoicewatcher.util.aes.AesUtil;
import picocli.CommandLine.Command;

import javax.crypto.SecretKey;
import java.util.Scanner;
import java.util.concurrent.Callable;

@Command(
    name = "encrypt",
    description = "Asks for the key and a value and prints the value encrypted with AES-GCM.",
    mixinStandardHelpOptions = true
)
public class EncryptCommand implements Callable<Integer> {

  @Override
  public Integer call() {
    try (Scanner scanner = new Scanner(System.in)) {
      String encodedKey = CommandUtil.promptForLine(scanner, "Enter encryption key");
      String input = CommandUtil.promptForLine(scanner, "Enter input for encryption");
      SecretKey secretKey = AesUtil.decodeSecretKey(encodedKey);
      String result = AesUtil.encrypt(input, secretKey);
      IO.println(result);
      return 0;
    }
  }
}

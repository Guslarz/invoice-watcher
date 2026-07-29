package com.guslarz.invoicewatcher.secret.helper.app.subcommand;

import com.guslarz.invoicewatcher.secret.helper.app.util.CommandUtil;
import com.guslarz.invoicewatcher.util.aes.AesUtil;
import lombok.SneakyThrows;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.spec.KeySpec;
import java.util.Scanner;
import java.util.concurrent.Callable;

@Command(name = "generate-key")
public class GenerateKeyCommand implements Callable<Integer> {

  private static final String PasswordMode = "password";
  private static final String RandomMode = "random";

  @Option(names = {"-m", "--mode"})
  private String mode = PasswordMode;

  @Override
  public Integer call() {
    SecretKey secretKey = switch (mode) {
      case PasswordMode -> generateSecretKeyByPassword();
      case RandomMode -> generateRandomSecretKey();
      default -> throw new IllegalArgumentException();
    };
    System.out.println(AesUtil.encodeSecretKey(secretKey));
    return 0;
  }

  private SecretKey generateSecretKeyByPassword() {
    try (Scanner scanner = new Scanner(System.in)) {
      String password = CommandUtil.promptForLine(scanner, "Enter encryption password");
      byte[] salt = generateSalt();
      return generateSecretKey(password, salt);
    }
  }

  private SecretKey generateRandomSecretKey() {
    throw new RuntimeException("Not implemented");
  }

  @SneakyThrows
  private SecretKey generateSecretKey(String password, byte[] salt) {
    SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
    KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 65536, 256);

    return AesUtil.toSecretKey(factory.generateSecret(spec).getEncoded());
  }

  private byte[] generateSalt() {
    byte[] bytes = new byte[16];
    AesUtil.RandomGenerator.nextBytes(bytes);
    return bytes;
  }
}

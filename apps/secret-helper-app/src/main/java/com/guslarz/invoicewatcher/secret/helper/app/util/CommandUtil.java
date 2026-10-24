package com.guslarz.invoicewatcher.secret.helper.app.util;

import java.util.Scanner;

public class CommandUtil {

  public static String promptForLine(Scanner scanner, String message) {
    IO.print(message + ": ");
    return scanner.nextLine();
  }
}

package com.guslarz.invoicewatcher.googleoauth.api;

public class GmailReconnectionRequiredException extends RuntimeException {
  public GmailReconnectionRequiredException() {
    super("Gmail authorization requires consent during sign-in");
  }
}

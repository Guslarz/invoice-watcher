package com.guslarz.invoicewatcher.oauth.google.api;

public class GmailReconnectionRequiredException extends RuntimeException {
  public GmailReconnectionRequiredException() {
    super("Gmail authorization requires consent during sign-in");
  }
}

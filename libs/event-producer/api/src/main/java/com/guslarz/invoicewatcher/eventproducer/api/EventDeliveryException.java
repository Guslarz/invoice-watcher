package com.guslarz.invoicewatcher.eventproducer.api;

public class EventDeliveryException extends RuntimeException {

  public EventDeliveryException(String message, Throwable cause) {
    super(message, cause);
  }
}

package com.guslarz.invoicewatcher.eventproducer.api;

public interface EventProducer {

  /** Checks effective application-topic settings before publication where supported. */
  default void verifyTopic(String topic, long retentionMs) {}

  /**
   * Sends the event and returns once the backend confirms it has stored it.
   *
   * @throws EventDeliveryException if the backend does not confirm the event
   */
  <T> void send(String topic, Event<T> event);
}

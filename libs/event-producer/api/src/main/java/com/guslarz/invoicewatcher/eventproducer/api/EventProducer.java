package com.guslarz.invoicewatcher.eventproducer.api;

public interface EventProducer {

  /**
   * Sends the event and returns once the backend confirms it has stored it.
   *
   * @throws EventDeliveryException if the backend does not confirm the event
   */
  void send(String topic, Event event);
}
